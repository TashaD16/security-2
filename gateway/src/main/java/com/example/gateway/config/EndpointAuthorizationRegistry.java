package com.example.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Реестр правил авторизации для эндпоинтов.
 * Хранит маппинг путь+метод -> метод CustomAuthorizationManager.
 */
@Component
public class EndpointAuthorizationRegistry {

    private static final Logger log = LoggerFactory.getLogger(EndpointAuthorizationRegistry.class);

    private final Map<String, BiFunction<Mono<Authentication>, AuthorizationContext, Mono<org.springframework.security.authorization.AuthorizationDecision>>> rules = 
            new ConcurrentHashMap<>();

    /**
     * Регистрирует правило авторизации для эндпоинта
     */
    public void register(String endpointKey, 
                        BiFunction<Mono<Authentication>, AuthorizationContext, Mono<org.springframework.security.authorization.AuthorizationDecision>> authorizationMethod) {
        rules.put(endpointKey, authorizationMethod);
    }

    /**
     * Очищает все зарегистрированные правила
     */
    public void clear() {
        rules.clear();
    }

    /**
     * Возвращает количество зарегистрированных правил
     */
    public int size() {
        return rules.size();
    }

    /**
     * Возвращает все зарегистрированные эндпоинты
     */
    public Set<String> getAllEndpoints() {
        return new HashSet<>(rules.keySet());
    }

    /**
     * Выводит в консоль все зарегистрированные эндпоинты
     */
    public void printAllEndpoints() {
        if (rules.isEmpty()) {
            log.info("=== EndpointAuthorizationRegistry: No endpoints registered ===");
            return;
        }

        log.info("=== EndpointAuthorizationRegistry: {} registered endpoints ===", rules.size());
        List<String> sortedEndpoints = new ArrayList<>(rules.keySet());
        Collections.sort(sortedEndpoints);
        
        for (String endpoint : sortedEndpoints) {
            log.info("  - {}", endpoint);
        }
        log.info("=== End of registered endpoints ===");
    }

    /**
     * Находит метод авторизации для эндпоинта
     */
    public BiFunction<Mono<Authentication>, AuthorizationContext, Mono<org.springframework.security.authorization.AuthorizationDecision>> findAuthorizationMethod(String method, String path) {
        // Ищем точное совпадение
        String key = method + ":" + path;
        BiFunction<Mono<Authentication>, AuthorizationContext, Mono<org.springframework.security.authorization.AuthorizationDecision>> methodRef = rules.get(key);
        
        // Если не найдено, ищем по паттерну (для path variables)
        if (methodRef == null) {
            methodRef = findByPattern(method, path);
        }
        
        return methodRef;
    }

    /**
     * Ищет метод авторизации по паттерну пути (для path variables)
     */
    private BiFunction<Mono<Authentication>, AuthorizationContext, Mono<org.springframework.security.authorization.AuthorizationDecision>> findByPattern(String method, String path) {
        for (Map.Entry<String, BiFunction<Mono<Authentication>, AuthorizationContext, Mono<org.springframework.security.authorization.AuthorizationDecision>>> entry : rules.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length != 2) continue;
            
            String entryMethod = parts[0];
            String entryPath = parts[1];
            
            if (!entryMethod.equals(method)) continue;
            
            // Простая проверка паттерна (заменяем {variable} на [^/]+)
            String pattern = entryPath.replaceAll("\\{[^}]+\\}", "[^/]+");
            if (path.matches("^" + pattern + "$")) {
                return entry.getValue();
            }
        }
        return null;
    }
}
