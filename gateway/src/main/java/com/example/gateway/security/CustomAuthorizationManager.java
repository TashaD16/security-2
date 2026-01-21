package com.example.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom Authorization Manager для проверки прав доступа.
 * Содержит методы для проверки различных типов доступа на основе authorities.
 */
@Component
public class CustomAuthorizationManager {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthorizationManager.class);

    /**
     * Проверка права чтения деклараций
     */
    public Mono<AuthorizationDecision> checkReadDeclaration(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        return checkAuthorities(authenticationMono, Set.of("READ_DECLARATION", "ADMIN"), 
                "read declaration");
    }

    /**
     * Проверка права записи деклараций
     */
    public Mono<AuthorizationDecision> checkWriteDeclaration(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        return checkAuthorities(authenticationMono, Set.of("WRITE_DECLARATION", "ADMIN"), 
                "write declaration");
    }

    /**
     * Проверка права утверждения деклараций
     */
    public Mono<AuthorizationDecision> checkApproveDeclaration(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        return checkAuthorities(authenticationMono, Set.of("APPROVE_DECLARATION", "ADMIN"), 
                "approve declaration");
    }

    /**
     * Проверка права чтения товаров
     */
    public Mono<AuthorizationDecision> checkReadWare(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        return checkAuthorities(authenticationMono, Set.of("READ_WARE", "ADMIN"), 
                "read ware");
    }

    /**
     * Проверка права записи товаров
     */
    public Mono<AuthorizationDecision> checkWriteWare(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        return checkAuthorities(authenticationMono, Set.of("WRITE_WARE", "ADMIN"), 
                "write ware");
    }

    /**
     * Проверка права управления инвентарем
     */
    public Mono<AuthorizationDecision> checkManageInventory(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        return checkAuthorities(authenticationMono, Set.of("MANAGE_INVENTORY", "ADMIN"), 
                "manage inventory");
    }

    /**
     * Общий метод для проверки authorities
     */
    private Mono<AuthorizationDecision> checkAuthorities(
            Mono<Authentication> authenticationMono,
            Set<String> requiredAuthorities,
            String action) {
        
        return authenticationMono
                .map(auth -> {
                    Set<String> userAuthorities = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toSet());
                    
                    boolean hasAccess = requiredAuthorities.stream()
                            .anyMatch(userAuthorities::contains);
                    
                    log.debug("Authorization check for {}: required={}, user={}, result={}", 
                            action, requiredAuthorities, userAuthorities, hasAccess);
                    
                    if (!hasAccess) {
                        log.warn("Access denied for {}: user={}, required={}", 
                                action, userAuthorities, requiredAuthorities);
                    }
                    
                    return new AuthorizationDecision(hasAccess);
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
