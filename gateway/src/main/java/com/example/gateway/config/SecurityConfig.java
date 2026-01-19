package com.example.gateway.config;

import com.example.gateway.security.AnnotationBasedAuthorizationManager;
import com.example.gateway.security.AuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

/**
 * Централизованная конфигурация безопасности для всех модулей приложения.
 * Вся логика безопасности (аутентификация и авторизация) обрабатывается здесь.
 * Авторизация выполняется на основе аннотаций из контроллеров модулей.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationConverter authenticationConverter;
    private final AnnotationBasedAuthorizationManager authorizationManager;

    /**
     * SecurityWebFilterChain - централизованная цепочка фильтров безопасности
     * для защиты всех API эндпоинтов.
     * Авторизация выполняется на основе аннотаций из контроллеров модулей.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable())
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            
            // Настройка аутентификации и авторизации
            // Авторизация выполняется через AnnotationBasedAuthorizationManager на основе аннотаций
            .authorizeExchange(exchanges -> exchanges
                // Публичные эндпоинты
                .pathMatchers("/actuator/health", "/actuator/info", "/public/**")
                    .permitAll()
                
                // Все API эндпоинты требуют аутентификации и авторизации
                // Детальная авторизация выполняется через AnnotationBasedAuthorizationManager
                .pathMatchers("/api/**")
                    .access(authorizationManager::checkAccess)
                
                // Все остальные запросы требуют аутентификации
                .anyExchange()
                    .authenticated()
            )
            
            // Добавление фильтров аутентификации
            .addFilterBefore(authenticationWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION);

        return http.build();
    }

    /**
     * Фильтр аутентификации для проверки пользователя
     */
    @Bean
    public AuthenticationWebFilter authenticationWebFilter() {
        // ReactiveAuthenticationManager - просто возвращает уже аутентифицированного пользователя
        org.springframework.security.authentication.ReactiveAuthenticationManager authenticationManager = 
            authentication -> Mono.just(authentication);
        
        AuthenticationWebFilter filter = new AuthenticationWebFilter(authenticationManager);
        filter.setServerAuthenticationConverter(authenticationConverter);
        return filter;
    }
}
