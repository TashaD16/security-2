package com.example.gateway.config;

import com.example.commons.security.annotation.*;
import com.example.gateway.security.CustomAuthorizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
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
@Slf4j
@Component
@RequiredArgsConstructor
public class ControllerScanner implements CommandLineRunner {

    private final CustomAuthorizationManager authorizationManager;
    private final EndpointAuthorizationRegistry endpointRegistry;
    
    @Value("${endpoint-scanner.scan-packages:com.example.moduleA.controller,com.example.moduleB.controller}")
    private String scanPackages;

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
        Set<Class<?>> controllers = scanControllers();
        if (!controllers.isEmpty()) {
            // Очищаем старые правила перед пересканированием
            endpointRegistry.clear();
            initializeAuthorizationRules(controllers);
            log.info("Rescan completed. Registered {} authorization rules", endpointRegistry.size());
        } else {
            log.warn("No controllers found. Make sure moduleA and moduleB are in classpath.");
        }
    }

    /**
     * Сканирует контроллеры из classpath
     */
    public Set<Class<?>> scanControllers() {
        Set<Class<?>> controllers = new HashSet<>();
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            
            // Получаем пакеты для сканирования из конфигурации
            String[] packageArray = scanPackages.split(",");
            String[] patterns = new String[packageArray.length];
            for (int i = 0; i < packageArray.length; i++) {
                String packagePath = packageArray[i].trim().replace('.', '/');
                patterns[i] = "classpath*:" + packagePath + "/**/*.class";
                log.debug("Scanning pattern: {}", patterns[i]);
            }
            
            for (String pattern : patterns) {
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
                                    log.debug("Found controller: {}", className);
                                }
                            } catch (ClassNotFoundException e) {
                                log.trace("Could not load class from resource: {}", resource, e);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not scan pattern: {}", pattern, e);
                }
            }
        } catch (Exception e) {
            log.error("Error scanning controllers", e);
        }
        
        log.info("Scanned {} controllers", controllers.size());
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
