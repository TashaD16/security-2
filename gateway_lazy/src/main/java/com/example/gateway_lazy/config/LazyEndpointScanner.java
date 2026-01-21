package com.example.gateway_lazy.config;

import com.example.gateway_lazy.config.ControllerScanningUtils;
import com.example.gateway_lazy.security.CustomAuthorizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Ленивый сканер эндпоинтов - сканирует конкретный эндпоинт при первом обращении к нему,
 * а не все эндпоинты при старте приложения.
 */
@Component
public class LazyEndpointScanner {

    private static final Logger log = LoggerFactory.getLogger(LazyEndpointScanner.class);

    private final EndpointAuthorizationRegistry endpointRegistry;
    private final CustomAuthorizationManager authorizationManager;
    
    // Кэш отсканированных контроллеров (путь -> класс контроллера)
    private final Map<String, Class<?>> scannedControllers = new ConcurrentHashMap<>();
    
    // Кэш проверенных путей (чтобы не сканировать повторно)
    private final Set<String> scannedPaths = ConcurrentHashMap.newKeySet();
    
    @Value("${endpoint-scanner.scan-packages:com.example.moduleA.controller,com.example.moduleB.controller}")
    private String scanPackages;
    
    @Value("${endpoint-scanner.lazy.auto-scan-all:false}")
    private boolean autoScanAll;

    public LazyEndpointScanner(EndpointAuthorizationRegistry endpointRegistry,
                               CustomAuthorizationManager authorizationManager) {
        this.endpointRegistry = endpointRegistry;
        this.authorizationManager = authorizationManager;
    }

    /**
     * Сканирует конкретный эндпоинт при обращении к нему.
     * Если эндпоинт уже отсканирован, возвращает true без повторного сканирования.
     * 
     * @param httpMethod HTTP метод (GET, POST, etc.)
     * @param path путь эндпоинта
     * @return true если эндпоинт найден и зарегистрирован
     */
    public boolean scanEndpointOnDemand(String httpMethod, String path) {
        String endpointKey = httpMethod + ":" + path;
        
        // Проверяем, не сканировали ли мы уже этот путь
        if (scannedPaths.contains(endpointKey)) {
            log.debug("Endpoint {} already scanned, skipping", endpointKey);
            return endpointRegistry.findAuthorizationMethod(httpMethod, path) != null;
        }
        
        log.debug("Lazy scanning endpoint: {} {}", httpMethod, path);
        
        // Пытаемся найти контроллер, содержащий этот эндпоинт
        Class<?> controllerClass = findControllerForPath(path);
        
        if (controllerClass != null) {
            // Сканируем только этот контроллер
            int registered = scanController(controllerClass);
            scannedPaths.add(endpointKey);
            log.info("Lazy scanned controller {} for endpoint {} {}, registered {} rules", 
                    controllerClass.getSimpleName(), httpMethod, path, registered);
            return endpointRegistry.findAuthorizationMethod(httpMethod, path) != null;
        }
        
        // Если не нашли контроллер, помечаем путь как проверенный
        scannedPaths.add(endpointKey);
        log.debug("No controller found for endpoint {} {}", httpMethod, path);
        return false;
    }

    /**
     * Находит контроллер, который может содержать указанный путь
     */
    private Class<?> findControllerForPath(String path) {
        // Сначала проверяем кэш
        Class<?> cached = scannedControllers.get(path);
        if (cached != null) {
            return cached;
        }
        
        try {
            Set<Class<?>> controllers;
            
            if (autoScanAll) {
                // Сканируем весь classpath
                controllers = scanAllControllersInClasspath();
            } else {
                // Сканируем только указанные пакеты
                controllers = scanControllersInPackages();
            }
            
            // Ищем контроллер, который может содержать этот путь
            for (Class<?> controllerClass : controllers) {
                if (controllerMatchesPath(controllerClass, path)) {
                    scannedControllers.put(path, controllerClass);
                    return controllerClass;
                }
            }
        } catch (Exception e) {
            log.error("Error finding controller for path: {}", path, e);
        }
        
        return null;
    }

    /**
     * Проверяет, соответствует ли контроллер указанному пути
     */
    private boolean controllerMatchesPath(Class<?> controllerClass, String path) {
        String basePath = ControllerScanningUtils.findBasePath(controllerClass);
        
        // Проверяем методы контроллера
        for (Method method : controllerClass.getDeclaredMethods()) {
            String httpMethod = ControllerScanningUtils.findHttpMethod(method);
            if (httpMethod == null) continue;
            
            String methodPath = ControllerScanningUtils.findMethodPath(method);
            String fullPath = basePath + methodPath;
            
            // Простая проверка совпадения (можно улучшить для path variables)
            if (path.equals(fullPath) || path.startsWith(fullPath + "/")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Сканирует контроллер и регистрирует его эндпоинты
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
                log.debug("Lazy registered authorization rule: {} -> {}", key, authorizationMethod);
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

    /**
     * Сканирует контроллеры в указанных пакетах
     */
    private Set<Class<?>> scanControllersInPackages() {
        Set<Class<?>> controllers = new HashSet<>();
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            
            String[] packageArray = scanPackages.split(",");
            
            for (String packageName : packageArray) {
                packageName = packageName.trim();
                String packagePath = packageName.replace('.', '/');
                String pattern = "classpath*:" + packagePath + "/**/*.class";
                
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
                                }
                            } catch (ClassNotFoundException e) {
                                // Игнорируем
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error scanning package: {}", packageName, e);
                }
            }
        } catch (Exception e) {
            log.error("Error scanning controllers", e);
        }
        
        return controllers;
    }

    /**
     * Сканирует все контроллеры в classpath
     */
    private Set<Class<?>> scanAllControllersInClasspath() {
        Set<Class<?>> controllers = new HashSet<>();
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            String pattern = "classpath*:**/*.class";
            Resource[] resources = resolver.getResources(pattern);
            
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    try {
                        MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                        String className = metadataReader.getClassMetadata().getClassName();
                        
                        if (isSystemClass(className)) {
                            continue;
                        }
                        
                        if (metadataReader.getAnnotationMetadata().hasAnnotation(RestController.class.getName())) {
                            Class<?> clazz = Class.forName(className);
                            controllers.add(clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        // Игнорируем
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error scanning all controllers", e);
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
}
