package com.example.gateway_lazy.security;

import com.example.gateway_lazy.config.EndpointAuthorizationRegistry;
import com.example.gateway_lazy.config.LazyEndpointScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

/**
 * Компонент для проверки авторизации на основе аннотаций из контроллеров.
 * Используется для интеграции в существующую SecurityConfig другого проекта.
 * 
 * Включает поддержку ленивого сканирования эндпоинтов - сканирует конкретный эндпоинт
 * при первом обращении к нему, а не все эндпоинты при старте приложения.
 * 
 * Полный цикл проверки:
 * 1. Получает путь и метод запроса
 * 2. Находит метод CustomAuthorizationManager из EndpointAuthorizationRegistry
 * 3. Если не найдено, пытается отсканировать эндпоинт через LazyEndpointScanner
 * 4. Вызывает соответствующий метод для проверки прав доступа
 */
@Component
public class AnnotationBasedAuthorizationChecker {

    private static final Logger log = LoggerFactory.getLogger(AnnotationBasedAuthorizationChecker.class);

    private final EndpointAuthorizationRegistry endpointRegistry;
    private final LazyEndpointScanner lazyScanner;

    public AnnotationBasedAuthorizationChecker(EndpointAuthorizationRegistry endpointRegistry,
                                               LazyEndpointScanner lazyScanner) {
        this.endpointRegistry = endpointRegistry;
        this.lazyScanner = lazyScanner;
    }

    /**
     * Проверяет авторизацию для запроса на основе аннотаций из контроллеров.
     * Этот метод можно использовать в SecurityConfig другого проекта.
     * 
     * Пример использования в SecurityConfig:
     * .pathMatchers("/api/**")
     *     .access(authorizationChecker::checkAuthorization)
     * 
     * @param authenticationMono Authentication объект пользователя
     * @param context AuthorizationContext с информацией о запросе
     * @return AuthorizationDecision - разрешить или запретить доступ
     */
    public Mono<AuthorizationDecision> checkAuthorization(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        ServerWebExchange exchange = context.getExchange();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        
        log.debug("Checking authorization for {} {}", method, path);
        
        // Находим метод авторизации из реестра
        BiFunction<Mono<Authentication>, AuthorizationContext, Mono<AuthorizationDecision>> authorizationMethod = 
            endpointRegistry.findAuthorizationMethod(method, path);
        
        // Если не найдено, пытаемся отсканировать эндпоинт
        if (authorizationMethod == null && lazyScanner != null) {
            log.debug("Authorization method not found, attempting lazy scan for {} {}", method, path);
            boolean scanned = lazyScanner.scanEndpointOnDemand(method, path);
            
            if (scanned) {
                // Пытаемся найти снова после сканирования
                authorizationMethod = endpointRegistry.findAuthorizationMethod(method, path);
            }
        }
        
        if (authorizationMethod != null) {
            // Вызываем соответствующий метод CustomAuthorizationManager
            log.debug("Found authorization method for {} {}", method, path);
            return authorizationMethod.apply(authenticationMono, context);
        }
        
        // Если нет правила для эндпоинта, разрешаем доступ
        // (аутентификация уже проверена в другом проекте)
        log.debug("No authorization rule found for {} {}, allowing access", method, path);
        return Mono.just(new AuthorizationDecision(true));
    }
}
