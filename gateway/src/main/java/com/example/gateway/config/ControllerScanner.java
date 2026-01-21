package com.example.gateway.config;

import com.example.gateway.config.ControllerScanningUtils;
import com.example.gateway.security.CustomAuthorizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import org.springframework.security.authorization.AuthorizationDecision;

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
     * 
     * @return множество найденных контроллеров
     */
    public Set<Class<?>> scanAllControllersInClasspath() {
        Set<Class<?>> controllers = new HashSet<>();
        
        try {
            log.info("Scanning all controllers in classpath...");
            
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            String pattern = "classpath*:**/*.class";
            Resource[] resources = resolver.getResources(pattern);
            
            int checked = 0;
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    try {
                        MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                        String className = metadataReader.getClassMetadata().getClassName();
                        
                        // Пропускаем системные классы
                        if (isSystemClass(className)) {
                            continue;
                        }
                        
                        checked++;
                        if (metadataReader.getAnnotationMetadata().hasAnnotation(RestController.class.getName())) {
                            Class<?> clazz = Class.forName(className);
                            controllers.add(clazz);
                            log.info("✓ Found controller: {}", className);
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        // Игнорируем
                    }
                }
            }
            
            log.info("Checked {} classes, found {} controllers", checked, controllers.size());
            
            if (controllers.isEmpty()) {
                log.warn("No controllers found in classpath");
            }
            
        } catch (Exception e) {
            log.error("Error scanning all controllers in classpath", e);
        }
        
        return controllers;
    }

    /**
     * Сканирует контроллеры из указанных пакетов (из конфигурации)
     */
    public Set<Class<?>> scanControllers() {
        Set<Class<?>> controllers = new HashSet<>();
        
        try {
            String[] packageArray = scanPackages.split(",");
            log.info("Scanning controllers in packages: {}", scanPackages);
            
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            
            for (String packageName : packageArray) {
                packageName = packageName.trim();
                String packagePath = packageName.replace('.', '/');
                String pattern = "classpath*:" + packagePath + "/**/*.class";
                
                log.debug("Scanning pattern: {}", pattern);
                
                try {
                    Resource[] resources = resolver.getResources(pattern);
                    
                    for (Resource resource : resources) {
                        if (resource.isReadable()) {
                            try {
                                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                                String className = metadataReader.getClassMetadata().getClassName();
                                
                                if (metadataReader.getAnnotationMetadata().hasAnnotation(RestController.class.getName())) {
                                    Class<?> clazz = Class.forName(className);
                                    controllers.add(clazz);
                                    log.info("✓ Found controller: {}", className);
                                }
                            } catch (ClassNotFoundException e) {
                                // Игнорируем
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error scanning package {}: {}", packageName, e.getMessage());
                }
            }
            
            log.info("Total controllers found: {}", controllers.size());
            
            if (controllers.isEmpty()) {
                log.warn("No controllers found in packages: {}", scanPackages);
            }
        } catch (Exception e) {
            log.error("Error scanning controllers", e);
        }
        
        return controllers;
    }

    /**
     * Проверяет, является ли класс системным (не нужно сканировать)
     */
    private boolean isSystemClass(String className) {
        return className.startsWith("org.springframework") ||
               className.startsWith("org.apache") ||
               className.startsWith("java.") ||
               className.startsWith("javax.") ||
               className.startsWith("jakarta.") ||
               className.contains("$");
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
        String basePath = ControllerScanningUtils.findBasePath(controllerClass);
        int rulesCount = 0;
        
        for (Method method : controllerClass.getDeclaredMethods()) {
            String httpMethod = ControllerScanningUtils.findHttpMethod(method);
            if (httpMethod == null) continue;

            String methodPath = ControllerScanningUtils.findMethodPath(method);
            String fullPath = basePath + methodPath;

            BiFunction<Mono<Authentication>, AuthorizationContext, Mono<AuthorizationDecision>> authorizationMethod = 
                ControllerScanningUtils.findAuthorizationMethod(method, this::getAuthorizationMethod);
            
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
     * Получает метод авторизации по типу аннотации
     */
    private BiFunction<Mono<Authentication>, AuthorizationContext, Mono<AuthorizationDecision>> 
            getAuthorizationMethod(String annotationType) {
        return switch (annotationType) {
            case "RequireReadDeclaration" -> authorizationManager::checkReadDeclaration;
            case "RequireWriteDeclaration" -> authorizationManager::checkWriteDeclaration;
            case "RequireApproveDeclaration" -> authorizationManager::checkApproveDeclaration;
            case "RequireReadWare" -> authorizationManager::checkReadWare;
            case "RequireWriteWare" -> authorizationManager::checkWriteWare;
            case "RequireManageInventory" -> authorizationManager::checkManageInventory;
            default -> null;
        };
    }
}
