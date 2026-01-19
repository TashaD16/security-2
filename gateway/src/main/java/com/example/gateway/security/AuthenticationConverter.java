package com.example.gateway.security;

import com.example.commons.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Конвертер для преобразования HTTP запроса в Authentication объект.
 * Извлекает userId из заголовка X-User-Id и создает Authentication с authorities.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationConverter implements ServerAuthenticationConverter {

    private final UserService userService;

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        // Извлекаем userId из заголовка
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        
        if (userId == null || userId.isEmpty()) {
            log.debug("No X-User-Id header found");
            return Mono.empty();
        }

        log.debug("Authenticating user with userId: {}", userId);

        // Получаем пользователя из UserService
        return Mono.fromCallable(() -> userService.findByUserId(userId))
                .flatMap(optional -> 
                    optional.map(Mono::just).orElse(Mono.empty())
                )
                .map(user -> {
                    // Создаем список authorities
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    
                    // Добавляем роли
                    user.getRoles().forEach(role -> 
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    
                    // Добавляем authorities
                    user.getAuthorities().forEach(auth -> 
                        authorities.add(new SimpleGrantedAuthority(auth))
                    );
                    
                    // Создаем Authentication объект
                    return new UserAuthentication(
                        user.getUsername(),
                        user.getUserId(),
                        authorities
                    );
                })
                .cast(Authentication.class)
                .doOnNext(auth -> log.debug("Successfully authenticated user: {}", 
                        ((UserAuthentication) auth).getName()))
                .doOnError(error -> log.error("Error during authentication", error));
    }
}
