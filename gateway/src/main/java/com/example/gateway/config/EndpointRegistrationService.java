package com.example.gateway.config;

import com.example.gateway.security.CustomAuthorizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.authorization.AuthorizationDecision;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Сервис для регистрации эндпоинтов от удаленных модулей (микросервисов).
 * Используется когда каждый модуль находится в отдельном контейнере.
 */
@Service
public class EndpointRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(EndpointRegistrationService.class);

    private final EndpointAuthorizationRegistry endpointRegistry;
    private final CustomAuthorizationManager authorizationManager;

    public EndpointRegistrationService(EndpointAuthorizationRegistry endpointRegistry,
                                      CustomAuthorizationManager authorizationManager) {
        this.endpointRegistry = endpointRegistry;
        this.authorizationManager = authorizationManager;
    }

    /**
     * Регистрирует эндпоинт от удаленного модуля
     * 
     * @param moduleName имя модуля (например, "moduleA", "moduleB")
     * @param httpMethod HTTP метод (GET, POST, PUT, DELETE, PATCH)
     * @param path путь эндпоинта (например, "/api/declarations")
     * @param annotationType тип аннотации безопасности (например, "RequireReadDeclaration")
     * @return true если регистрация успешна
     */
    public boolean registerEndpoint(String moduleName, String httpMethod, String path, String annotationType) {
        try {
            String key = httpMethod + ":" + path;
            BiFunction<Mono<Authentication>, AuthorizationContext, Mono<AuthorizationDecision>> authorizationMethod = 
                getAuthorizationMethod(annotationType);
            
            if (authorizationMethod == null) {
                log.warn("Unknown annotation type: {} for endpoint {}:{} from module {}", 
                        annotationType, httpMethod, path, moduleName);
                return false;
            }
            
            endpointRegistry.register(key, authorizationMethod);
            log.info("✓ Registered endpoint from module {}: {}:{} -> {}", 
                    moduleName, httpMethod, path, annotationType);
            return true;
        } catch (Exception e) {
            log.error("Error registering endpoint from module {}: {}:{}", 
                     moduleName, httpMethod, path, e);
            return false;
        }
    }

    /**
     * Регистрирует несколько эндпоинтов от модуля
     * 
     * @param moduleName имя модуля
     * @param endpoints список эндпоинтов
     * @return количество успешно зарегистрированных эндпоинтов
     */
    public int registerEndpoints(String moduleName, List<EndpointInfo> endpoints) {
        int registered = 0;
        for (EndpointInfo endpoint : endpoints) {
            if (registerEndpoint(moduleName, endpoint.httpMethod, endpoint.path, endpoint.annotationType)) {
                registered++;
            }
        }
        log.info("Registered {}/{} endpoints from module {}", registered, endpoints.size(), moduleName);
        return registered;
    }

    /**
     * Получает метод авторизации по типу аннотации
     */
    private BiFunction<Mono<Authentication>, AuthorizationContext, Mono<AuthorizationDecision>> 
            getAuthorizationMethod(String annotationType) {
        switch (annotationType) {
            case "RequireReadDeclaration":
                return authorizationManager::checkReadDeclaration;
            case "RequireWriteDeclaration":
                return authorizationManager::checkWriteDeclaration;
            case "RequireApproveDeclaration":
                return authorizationManager::checkApproveDeclaration;
            case "RequireReadWare":
                return authorizationManager::checkReadWare;
            case "RequireWriteWare":
                return authorizationManager::checkWriteWare;
            case "RequireManageInventory":
                return authorizationManager::checkManageInventory;
            default:
                return null;
        }
    }

    /**
     * Информация об эндпоинте для регистрации
     */
    public static class EndpointInfo {
        public String httpMethod;
        public String path;
        public String annotationType;

        public EndpointInfo() {
        }

        public EndpointInfo(String httpMethod, String path, String annotationType) {
            this.httpMethod = httpMethod;
            this.path = path;
            this.annotationType = annotationType;
        }
    }
}
