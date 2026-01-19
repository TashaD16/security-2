package com.example.gateway.security;

import com.example.gateway.config.EndpointAuthorizationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Полный цикл проверки:
 * 1. Получает путь и метод запроса
 * 2. Находит метод CustomAuthorizationManager из EndpointAuthorizationRegistry
 * 3. Вызывает соответствующий метод для проверки прав доступа
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnotationBasedAuthorizationChecker {

    private final EndpointAuthorizationRegistry endpointRegistry;

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
