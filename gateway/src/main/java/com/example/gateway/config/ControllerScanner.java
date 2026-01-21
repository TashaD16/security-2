package com.example.gateway.config;

import com.example.commons.security.annotation.*;
import com.example.gateway.security.CustomAuthorizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Сканер контроллеров для создания маппинга эндпоинт -> метод CustomAuthorizationManager.
 * Сканирует контроллеры из модулей moduleA и moduleB и создает правила авторизации.
 */
@Component
public class ControllerScanner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ControllerScanner.class);

    private final CustomAuthorizationManager authorizationManager;
    private final EndpointAuthorizationRegistry endpointRegistry;
    
    @Value("${endpoint-scanner.scan-packages:com.example.moduleA.controller,com.example.moduleB.controller}")
    private String scanPackages;
    
    @Value("${endpoint-scanner.auto-scan-all:false}")
    private boolean autoScanAll;

    public ControllerScanner(CustomAuthorizationManager authorizationManager,
                             EndpointAuthorizationRegistry endpointRegistry) {
        this.authorizationManager = authorizationManager;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    public void run(String... args) throws Exception {
        rescan();
    }

    /**
     * Пересканирует контроллеры и обновляет правила авторизации.
     * Может быть вызван вручную при обновлении модулей во время работы приложения.
     */
    public void rescan() {
        log.info("Starting controller rescan...");
        Set<Class<?>> controllers;
        
        if (autoScanAll) {
            log.info("Using automatic classpath scanning (scanAllControllersInClasspath)");
            controllers = scanAllControllersInClasspath();
        } else {
            log.info("Using configured package scanning (scanControllers)");
            controllers = scanControllers();
        }
        if (!controllers.isEmpty()) {
            // Очищаем старые правила перед пересканированием
            endpointRegistry.clear();
            initializeAuthorizationRules(controllers);
            log.info("Rescan completed. Registered {} authorization rules", endpointRegistry.size());
            // Выводим все зарегистрированные эндпоинты в консоль
            endpointRegistry.printAllEndpoints();
        } else {
            log.warn("No controllers found!");
            log.warn("Configured scan packages: {}", scanPackages);
            log.warn("Please check:");
            log.warn("  1. Are controllers in the specified packages?");
            log.warn("  2. Do controllers have @RestController annotation?");
            log.warn("  3. Are modules with controllers in classpath?");
            log.warn("  4. Update 'endpoint-scanner.scan-packages' in application.properties with correct package names");
        }
    }

    /**
     * Автоматически находит все контроллеры во всех модулях в classpath.
     * Сканирует весь classpath без необходимости указывать конкретные пакеты.
     * Использует ControllerFinder для упрощения логики
     * 
     * @return множество найденных контроллеров
     */
    public Set<Class<?>> scanAllControllersInClasspath() {
        Set<Class<?>> controllers = new HashSet<>();
        
        try {
            log.info("Scanning all controllers in classpath...");
            
            List<ControllerFinder.ControllerInfo> found = ControllerFinder.findAllControllers();
            
            for (ControllerFinder.ControllerInfo info : found) {
                try {
                    Class<?> clazz = Class.forName(info.className);
                    controllers.add(clazz);
                    log.info("✓ Registered controller: {}", info.className);
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    log.warn("Could not load controller class: {}", info.className, e);
                }
            }
            
            log.info("Total controllers found: {}", controllers.size());
            
            if (controllers.isEmpty()) {
                log.warn("No controllers found in classpath");
                log.warn("Use ControllerFinder.findAllControllers() for detailed diagnostics");
            }
            
        } catch (Exception e) {
            log.error("Error scanning all controllers in classpath", e);
        }
        
        return controllers;
    }

    /**
     * Сканирует контроллеры из указанных пакетов (из конфигурации)
     * Использует ControllerFinder для упрощения логики
     */
    public Set<Class<?>> scanControllers() {
        Set<Class<?>> controllers = new HashSet<>();
        
        try {
            String[] packageArray = scanPackages.split(",");
            log.info("Scanning controllers in packages: {}", scanPackages);
            
            for (String packageName : packageArray) {
                packageName = packageName.trim();
                log.info("Scanning package: {}", packageName);
                
                List<ControllerFinder.ControllerInfo> found = ControllerFinder.findControllers(packageName);
                
                for (ControllerFinder.ControllerInfo info : found) {
                    try {
                        Class<?> clazz = Class.forName(info.className);
                        controllers.add(clazz);
                        log.info("✓ Registered controller: {}", info.className);
                    } catch (ClassNotFoundException e) {
                        log.warn("Could not load controller class: {}", info.className, e);
                    }
                }
            }
            
            log.info("Total controllers found: {}", controllers.size());
            
            if (controllers.isEmpty()) {
                log.warn("No controllers found in packages: {}", scanPackages);
                log.warn("Use ControllerFinder.findControllers(\"{}\") for detailed diagnostics", 
                        scanPackages.split(",")[0]);
            }
        } catch (Exception e) {
            log.error("Error scanning controllers", e);
        }
        
        return controllers;
    }

    /**
     * Инициализирует правила авторизации на основе аннотаций из контроллеров
     */
    public void initializeAuthorizationRules(Set<Class<?>> controllerClasses) {
        log.info("Initializing authorization rules for {} controllers", controllerClasses.size());
        
        int rulesCount = 0;
        for (Class<?> controllerClass : controllerClasses) {
            rulesCount += scanController(controllerClass);
        }
        
        log.info("Registered {} authorization rules", rulesCount);
    }

    /**
     * Сканирует контроллер и создает правила авторизации для всех методов
     */
    private int scanController(Class<?> controllerClass) {
        RequestMapping classMapping = AnnotationUtils.findAnnotation(controllerClass, RequestMapping.class);
        String basePath = classMapping != null && classMapping.value().length > 0 
            ? classMapping.value()[0] 
            : "";

        int rulesCount = 0;
        for (Method method : controllerClass.getDeclaredMethods()) {
            String httpMethod = findHttpMethod(method);
            if (httpMethod == null) continue;

            String methodPath = findMethodPath(method);
            String fullPath = basePath + methodPath;

            // Ищем аннотацию безопасности и определяем метод CustomAuthorizationManager
            BiFunction<Mono<Authentication>, org.springframework.security.web.server.authorization.AuthorizationContext, Mono<org.springframework.security.authorization.AuthorizationDecision>> authorizationMethod = 
                findAuthorizationMethod(method);
            
            if (authorizationMethod != null) {
                String key = httpMethod + ":" + fullPath;
                endpointRegistry.register(key, authorizationMethod);
                rulesCount++;
                log.debug("Registered authorization rule: {} -> {}", key, authorizationMethod);
            }
        }
        
        return rulesCount;
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
     * Находит метод CustomAuthorizationManager на основе аннотации безопасности
     */
    private BiFunction<Mono<Authentication>, org.springframework.security.web.server.authorization.AuthorizationContext, Mono<org.springframework.security.authorization.AuthorizationDecision>> findAuthorizationMethod(Method method) {
        if (AnnotationUtils.findAnnotation(method, RequireReadDeclaration.class) != null) {
            return authorizationManager::checkReadDeclaration;
        }
        if (AnnotationUtils.findAnnotation(method, RequireWriteDeclaration.class) != null) {
            return authorizationManager::checkWriteDeclaration;
        }
        if (AnnotationUtils.findAnnotation(method, RequireApproveDeclaration.class) != null) {
            return authorizationManager::checkApproveDeclaration;
        }
        if (AnnotationUtils.findAnnotation(method, RequireReadWare.class) != null) {
            return authorizationManager::checkReadWare;
        }
        if (AnnotationUtils.findAnnotation(method, RequireWriteWare.class) != null) {
            return authorizationManager::checkWriteWare;
        }
        if (AnnotationUtils.findAnnotation(method, RequireManageInventory.class) != null) {
            return authorizationManager::checkManageInventory;
        }
        return null;
    }
}
