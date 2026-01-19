package com.example.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для автоматического пересканирования контроллеров при изменении модулей.
 * Периодически проверяет время модификации классов контроллеров и пересканирует при изменениях.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gateway.auto-rescan.enabled", havingValue = "true", matchIfMissing = false)
public class AutoRescanService {

    private final ControllerScanner controllerScanner;
    private final Map<String, Long> lastModifiedMap = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    
    @Value("${endpoint-scanner.scan-packages:com.example.moduleA.controller,com.example.moduleB.controller}")
    private String scanPackages;

    /**
     * Инициализация - сохраняет текущее время модификации всех контроллеров
     */
    @javax.annotation.PostConstruct
    public void init() {
        try {
            scanAndStoreModificationTimes();
            initialized = true;
            log.info("Auto-rescan service initialized. Monitoring controller changes every {} seconds", 
                    getPollingInterval());
        } catch (Exception e) {
            log.error("Failed to initialize auto-rescan service", e);
        }
    }

    /**
     * Периодически проверяет изменения в контроллерах и пересканирует при необходимости
     */
    @Scheduled(fixedDelayString = "${gateway.auto-rescan.polling-interval:30000}", initialDelayString = "${gateway.auto-rescan.initial-delay:30000}")
    public void checkAndRescan() {
        if (!initialized) {
            return;
        }

        try {
            boolean hasChanges = false;
            Set<String> currentControllerClasses = findControllerClasses();

            // Проверяем изменения в существующих контроллерах
            for (String className : currentControllerClasses) {
                long currentModified = getClassModificationTime(className);
                Long lastModified = lastModifiedMap.get(className);

                if (lastModified == null) {
                    // Новый контроллер
                    hasChanges = true;
                    log.debug("New controller detected: {}", className);
                    lastModifiedMap.put(className, currentModified);
                } else if (currentModified > lastModified && currentModified > 0) {
                    // Изменен существующий контроллер
                    hasChanges = true;
                    log.debug("Detected change in controller: {} (was: {}, now: {})", 
                            className, lastModified, currentModified);
                    lastModifiedMap.put(className, currentModified);
                } else if (currentModified > 0) {
                    // Обновляем время модификации
                    lastModifiedMap.put(className, currentModified);
                }
            }

            // Проверяем удаленные контроллеры
            Set<String> currentClasses = new HashSet<>(currentControllerClasses);
            for (String className : new HashSet<>(lastModifiedMap.keySet())) {
                if (!currentClasses.contains(className)) {
                    hasChanges = true;
                    log.debug("Controller removed: {}", className);
                    lastModifiedMap.remove(className);
                }
            }

            // Дополнительная проверка: сравниваем количество найденных контроллеров
            Set<Class<?>> scannedControllers = controllerScanner.scanControllers();
            Set<String> scannedClassNames = new HashSet<>();
            for (Class<?> clazz : scannedControllers) {
                scannedClassNames.add(clazz.getName());
            }

            if (scannedClassNames.size() != lastModifiedMap.size()) {
                hasChanges = true;
                log.debug("Controller count changed: was {}, now {}", 
                        lastModifiedMap.size(), scannedClassNames.size());
            }

            if (hasChanges) {
                log.info("Detected changes in controllers. Auto-rescanning...");
                controllerScanner.rescan();
                // Обновляем время модификации после пересканирования
                scanAndStoreModificationTimes();
            }
        } catch (Exception e) {
            log.error("Error during auto-rescan check", e);
        }
    }

    /**
     * Сканирует и сохраняет время модификации всех контроллеров
     */
    private void scanAndStoreModificationTimes() {
        Set<String> controllerClasses = findControllerClasses();
        for (String className : controllerClasses) {
            long modified = getClassModificationTime(className);
            lastModifiedMap.put(className, modified);
        }
    }

    /**
     * Находит все классы контроллеров
     */
    private Set<String> findControllerClasses() {
        Set<String> classes = new HashSet<>();
        try {
            // Получаем пакеты для сканирования из конфигурации
            String[] packageArray = scanPackages.split(",");
            
            for (String packageName : packageArray) {
                packageName = packageName.trim();
                String resourcePath = packageName.replace('.', '/');
                
                try {
                    java.util.Enumeration<URL> resources = 
                        Thread.currentThread().getContextClassLoader()
                            .getResources(resourcePath);
                    
                    while (resources.hasMoreElements()) {
                        URL url = resources.nextElement();
                        if ("file".equals(url.getProtocol())) {
                            File dir = new File(url.toURI());
                            scanDirectory(dir, packageName, classes);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error scanning package: {}", packageName, e);
                }
            }
        } catch (Exception e) {
            log.debug("Error finding controller classes", e);
        }
        return classes;
    }

    /**
     * Сканирует директорию и находит классы контроллеров
     */
    private void scanDirectory(File dir, String packageName, Set<String> classes) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)) {
                        classes.add(className);
                    }
                } catch (ClassNotFoundException e) {
                    // Игнорируем
                }
            }
        }
    }

    /**
     * Получает время модификации класса
     */
    private long getClassModificationTime(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            String resourceName = className.replace('.', '/') + ".class";
            URL resource = clazz.getClassLoader().getResource(resourceName);
            
            if (resource != null && "file".equals(resource.getProtocol())) {
                try {
                    File file = new File(resource.toURI());
                    return file.lastModified();
                } catch (Exception e) {
                    // Игнорируем
                }
            }
        } catch (ClassNotFoundException e) {
            // Игнорируем
        }
        return 0;
    }

    /**
     * Получает интервал опроса из конфигурации
     */
    private long getPollingInterval() {
        // Значение по умолчанию 30 секунд
        return 30000;
    }
}
