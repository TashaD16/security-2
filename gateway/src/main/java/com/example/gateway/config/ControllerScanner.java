package com.example.gateway.config;

import com.example.gateway.security.AnnotationBasedAuthorizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

/**
 * Сканер контроллеров для инициализации AnnotationBasedAuthorizationManager.
 * Сканирует контроллеры из модулей moduleA и moduleB через classpath.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ControllerScanner implements CommandLineRunner {

    private final AnnotationBasedAuthorizationManager authorizationManager;

    @Override
    public void run(String... args) throws Exception {
        Set<Class<?>> controllers = scanControllers();
        if (!controllers.isEmpty()) {
            authorizationManager.initialize(controllers);
        } else {
            log.warn("No controllers found. Make sure moduleA and moduleB are in classpath or configure endpoints manually.");
        }
    }

    /**
     * Сканирует контроллеры из classpath
     */
    private Set<Class<?>> scanControllers() {
        Set<Class<?>> controllers = new HashSet<>();
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            
            // Сканируем контроллеры из moduleA и moduleB
            String[] patterns = {
                "classpath*:com/example/moduleA/controller/**/*.class",
                "classpath*:com/example/moduleB/controller/**/*.class"
            };
            
            for (String pattern : patterns) {
                try {
                    Resource[] resources = resolver.getResources(pattern);
                    for (Resource resource : resources) {
                        if (resource.isReadable()) {
                            try {
                                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                                String className = metadataReader.getClassMetadata().getClassName();
                                
                                // Проверяем, есть ли аннотация @RestController
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
}
