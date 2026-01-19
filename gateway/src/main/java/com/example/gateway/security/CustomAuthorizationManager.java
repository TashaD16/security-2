package com.example.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom Authorization Manager for handling authorization logic
 * with path variable extraction and custom access control.
 * 
 * This class provides methods that are used to create ReactiveAuthorizationManager
 * instances for Spring Security WebFlux.
 */
@Component
public class CustomAuthorizationManager {

    private static final Logger logger = LoggerFactory.getLogger(CustomAuthorizationManager.class);

    // Authority constants
    private static final String AUTHORITY_ADMIN = "ADMIN";
    private static final String AUTHORITY_READ_DECLARATION = "READ_DECLARATION";
    private static final String AUTHORITY_WRITE_DECLARATION = "WRITE_DECLARATION";
    private static final String AUTHORITY_APPROVE_DECLARATION = "APPROVE_DECLARATION";
    private static final String AUTHORITY_READ_WARE = "READ_WARE";
    private static final String AUTHORITY_WRITE_WARE = "WRITE_WARE";
    private static final String AUTHORITY_MANAGE_INVENTORY = "MANAGE_INVENTORY";

    // Path variable names
    private static final String PATH_VAR_DECLARATION_ID = "declarationId";
    private static final String PATH_VAR_WARE_ID = "wareId";

    /**
     * Check read access for declarations
     */
    public Mono<AuthorizationDecision> checkReadDeclaration(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        String declarationId = extractPathVariableFromContext(context, PATH_VAR_DECLARATION_ID);
        
        return authenticationMono
                .doOnNext(auth -> logger.info("Checking read access for declaration: {} by user: {}", 
                        declarationId, auth.getName()))
                .flatMap(authentication -> checkAccess(authentication, AUTHORITY_READ_DECLARATION))
                .switchIfEmpty(Mono.just(new AuthorizationDecision(false)));
    }

    /**
     * Check write access for declarations
     */
    public Mono<AuthorizationDecision> checkWriteDeclaration(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        String declarationId = extractPathVariableFromContext(context, PATH_VAR_DECLARATION_ID);
        
        return authenticationMono
                .doOnNext(auth -> logger.info("Checking write access for declaration: {} by user: {}", 
                        declarationId, auth.getName()))
                .flatMap(authentication -> checkAccess(authentication, AUTHORITY_WRITE_DECLARATION))
                .switchIfEmpty(Mono.just(new AuthorizationDecision(false)));
    }

    /**
     * Check access for declaration approval (moduleA specific)
     */
    public Mono<AuthorizationDecision> checkApproveDeclaration(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        String declarationId = extractPathVariableFromContext(context, PATH_VAR_DECLARATION_ID);
        
        return authenticationMono
                .doOnNext(auth -> logger.info("Checking approve access for declaration: {} by user: {}", 
                        declarationId, auth.getName()))
                .flatMap(authentication -> checkAccess(authentication, AUTHORITY_APPROVE_DECLARATION))
                .switchIfEmpty(Mono.just(new AuthorizationDecision(false)));
    }

    /**
     * Check read access for wares
     */
    public Mono<AuthorizationDecision> checkReadWare(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        String wareId = extractPathVariableFromContext(context, PATH_VAR_WARE_ID);
        
        return authenticationMono
                .doOnNext(auth -> logger.info("Checking read access for ware: {} by user: {}", 
                        wareId, auth.getName()))
                .flatMap(authentication -> checkAccess(authentication, AUTHORITY_READ_WARE))
                .switchIfEmpty(Mono.just(new AuthorizationDecision(false)));
    }

    /**
     * Check write access for wares
     */
    public Mono<AuthorizationDecision> checkWriteWare(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        String wareId = extractPathVariableFromContext(context, PATH_VAR_WARE_ID);
        
        return authenticationMono
                .doOnNext(auth -> logger.info("Checking write access for ware: {} by user: {}", 
                        wareId, auth.getName()))
                .flatMap(authentication -> checkAccess(authentication, AUTHORITY_WRITE_WARE))
                .switchIfEmpty(Mono.just(new AuthorizationDecision(false)));
    }

    /**
     * Check access for ware inventory (moduleB specific)
     */
    public Mono<AuthorizationDecision> checkWareInventory(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        String wareId = extractPathVariableFromContext(context, PATH_VAR_WARE_ID);
        
        return authenticationMono
                .doOnNext(auth -> logger.info("Checking inventory access for ware: {} by user: {}", 
                        wareId, auth.getName()))
                .flatMap(authentication -> checkAccess(authentication, AUTHORITY_MANAGE_INVENTORY))
                .switchIfEmpty(Mono.just(new AuthorizationDecision(false)));
    }

    /**
     * Check access for general CRUD operations
     */
    public Mono<AuthorizationDecision> checkGeneralAccess(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        ServerWebExchange exchange = context.getExchange();
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        
        return authenticationMono
                .doOnNext(auth -> logger.debug("Checking general access for {} {} by user: {}", 
                        method, path, auth.getName()))
                .flatMap(authentication -> {
                    if (isAuthenticated(authentication)) {
                        return Mono.just(new AuthorizationDecision(true));
                    }
                    return Mono.just(new AuthorizationDecision(false));
                })
                .switchIfEmpty(Mono.just(new AuthorizationDecision(false)));
    }

    /**
     * Common method for checking access with authority
     */
    private Mono<AuthorizationDecision> checkAccess(
            Authentication authentication,
            String requiredAuthority) {
        
        if (!isAuthenticated(authentication)) {
            return Mono.just(new AuthorizationDecision(false));
        }
        
        boolean hasAccess = hasAuthority(authentication, requiredAuthority);
        
        logger.debug("Access decision: {} for authority: {}", hasAccess, requiredAuthority);
        return Mono.just(new AuthorizationDecision(hasAccess));
    }

    /**
     * Check if user is authenticated
     */
    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * Check if user has the required authority or ADMIN authority
     */
    private boolean hasAuthority(Authentication authentication, String requiredAuthority) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(requiredAuthority) ||
                             a.getAuthority().equals(AUTHORITY_ADMIN));
    }

    /**
     * Extract path variable from authorization context
     */
    private String extractPathVariableFromContext(AuthorizationContext context, String variableName) {
        ServerWebExchange exchange = context.getExchange();
        String path = exchange.getRequest().getPath().value();
        return extractPathVariable(path, variableName);
    }

    /**
     * Extract path variable from URI path
     * Supports both /declarations/{id} and /wares/{id} patterns
     */
    private String extractPathVariable(String path, String variableName) {
        // Map variable names to actual path segments
        String pathSegment;
        if (PATH_VAR_DECLARATION_ID.equals(variableName)) {
            pathSegment = "declarations";
        } else if (PATH_VAR_WARE_ID.equals(variableName)) {
            pathSegment = "wares";
        } else {
            pathSegment = variableName;
        }
        
        // Pattern to match /{pathSegment}/{id} or /{pathSegment}/{id}/...
        Pattern pattern = Pattern.compile("/" + pathSegment + "/([^/]+)");
        Matcher matcher = pattern.matcher(path);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Try to match at the end of path
        pattern = Pattern.compile("/" + pathSegment + "/([^/?]+)");
        matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
}
