package com.example.gateway.security;

import com.example.commons.security.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AuthorizationManager, который проверяет права доступа на основе аннотаций из контроллеров.
 * Сканирует контроллеры модулей и создает маппинг путь+метод -> требуемая аннотация.
 */
@Slf4j
@Component
public class AnnotationBasedAuthorizationManager {

    // Кэш для маппинга путь+метод -> требуемые authorities
    private final Map<String, Set<String>> endpointAuthorities = new ConcurrentHashMap<>();
    
    // Паттерны для маппинга аннотаций к authorities
    private static final Map<Class<? extends Annotation>, Set<String>> ANNOTATION_TO_AUTHORITIES = Map.of(
        RequireReadDeclaration.class, Set.of("READ_DECLARATION", "ADMIN"),
        RequireWriteDeclaration.class, Set.of("WRITE_DECLARATION", "ADMIN"),
        RequireApproveDeclaration.class, Set.of("APPROVE_DECLARATION", "ADMIN"),
        RequireReadWare.class, Set.of("READ_WARE", "ADMIN"),
        RequireWriteWare.class, Set.of("WRITE_WARE", "ADMIN"),
        RequireManageInventory.class, Set.of("MANAGE_INVENTORY", "ADMIN")
    );

    /**
     * Инициализация - сканирует контроллеры и создает маппинг
     */
    public void initialize(Set<Class<?>> controllerClasses) {
        log.info("Initializing annotation-based authorization for {} controllers", controllerClasses.size());
        
        for (Class<?> controllerClass : controllerClasses) {
            scanController(controllerClass);
        }
        
        log.info("Registered {} endpoint authorization rules", endpointAuthorities.size());
    }

    /**
     * Сканирует контроллер и создает маппинг для всех методов
     */
    private void scanController(Class<?> controllerClass) {
        RequestMapping classMapping = AnnotationUtils.findAnnotation(controllerClass, RequestMapping.class);
        String basePath = classMapping != null && classMapping.value().length > 0 
            ? classMapping.value()[0] 
            : "";

        for (Method method : controllerClass.getDeclaredMethods()) {
            // Ищем HTTP метод аннотацию
            String httpMethod = findHttpMethod(method);
            if (httpMethod == null) continue;

            // Ищем путь метода
            String methodPath = findMethodPath(method);
            String fullPath = basePath + methodPath;

            // Ищем аннотацию безопасности
            Set<String> requiredAuthorities = findRequiredAuthorities(method);
            if (!requiredAuthorities.isEmpty()) {
                String key = httpMethod + ":" + fullPath;
                endpointAuthorities.put(key, requiredAuthorities);
                log.debug("Registered authorization: {} -> {}", key, requiredAuthorities);
            }
        }
    }

    /**
     * Находит HTTP метод из аннотаций
     */
    private String findHttpMethod(Method method) {
        if (AnnotationUtils.findAnnotation(method, GetMapping.class) != null) return "GET";
        if (AnnotationUtils.findAnnotation(method, PostMapping.class) != null) return "POST";
        if (AnnotationUtils.findAnnotation(method, PutMapping.class) != null) return "PUT";
        if (AnnotationUtils.findAnnotation(method, DeleteMapping.class) != null) return "DELETE";
        if (AnnotationUtils.findAnnotation(method, PatchMapping.class) != null) return "PATCH";
        return null;
    }

    /**
     * Находит путь метода
     */
    private String findMethodPath(Method method) {
        GetMapping getMapping = AnnotationUtils.findAnnotation(method, GetMapping.class);
        if (getMapping != null && getMapping.value().length > 0) return getMapping.value()[0];
        
        PostMapping postMapping = AnnotationUtils.findAnnotation(method, PostMapping.class);
        if (postMapping != null && postMapping.value().length > 0) return postMapping.value()[0];
        
        PutMapping putMapping = AnnotationUtils.findAnnotation(method, PutMapping.class);
        if (putMapping != null && putMapping.value().length > 0) return putMapping.value()[0];
        
        DeleteMapping deleteMapping = AnnotationUtils.findAnnotation(method, DeleteMapping.class);
        if (deleteMapping != null && deleteMapping.value().length > 0) return deleteMapping.value()[0];
        
        PatchMapping patchMapping = AnnotationUtils.findAnnotation(method, PatchMapping.class);
        if (patchMapping != null && patchMapping.value().length > 0) return patchMapping.value()[0];
        
        RequestMapping requestMapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if (requestMapping != null && requestMapping.value().length > 0) return requestMapping.value()[0];
        
        return "";
    }

    /**
     * Находит требуемые authorities из аннотаций безопасности
     */
    private Set<String> findRequiredAuthorities(Method method) {
        Set<String> authorities = new HashSet<>();
        
        for (Map.Entry<Class<? extends Annotation>, Set<String>> entry : ANNOTATION_TO_AUTHORITIES.entrySet()) {
            if (AnnotationUtils.findAnnotation(method, entry.getKey()) != null) {
                authorities.addAll(entry.getValue());
            }
        }
        
        return authorities;
    }

    /**
     * Проверяет доступ к эндпоинту
     */
    public Mono<AuthorizationDecision> checkAccess(
            Mono<Authentication> authenticationMono,
            AuthorizationContext context) {
        
        ServerWebExchange exchange = context.getExchange();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        
        // Ищем точное совпадение
        String key = method + ":" + path;
        Set<String> requiredAuthorities = endpointAuthorities.get(key);
        
        // Если не найдено, ищем по паттерну (для path variables)
        if (requiredAuthorities == null) {
            requiredAuthorities = findAuthoritiesByPattern(method, path);
        }
        
        if (requiredAuthorities == null || requiredAuthorities.isEmpty()) {
            // Если нет правил для этого эндпоинта, разрешаем доступ (аутентификация уже проверена)
            log.debug("No authorization rules for {} {}, allowing access", method, path);
            return Mono.just(new AuthorizationDecision(true));
        }
        
        return authenticationMono
                .map(auth -> {
                    Set<String> userAuthorities = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toSet());
                    
                    // Проверяем, есть ли хотя бы одно требуемое authority
                    boolean hasAccess = requiredAuthorities.stream()
                            .anyMatch(userAuthorities::contains);
                    
                    log.debug("Authorization check for {} {}: required={}, user={}, result={}", 
                            method, path, requiredAuthorities, userAuthorities, hasAccess);
                    
                    return new AuthorizationDecision(hasAccess);
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    /**
     * Ищет authorities по паттерну пути (для path variables)
     */
    private Set<String> findAuthoritiesByPattern(String method, String path) {
        for (Map.Entry<String, Set<String>> entry : endpointAuthorities.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length != 2) continue;
            
            String entryMethod = parts[0];
            String entryPath = parts[1];
            
            if (!entryMethod.equals(method)) continue;
            
            // Простая проверка паттерна (заменяем {variable} на .+)
            String pattern = entryPath.replaceAll("\\{[^}]+\\}", "[^/]+");
            if (path.matches("^" + pattern + "$")) {
                return entry.getValue();
            }
        }
        return null;
    }
}
