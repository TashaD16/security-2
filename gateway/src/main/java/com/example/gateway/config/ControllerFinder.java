package com.example.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Простой утилитный класс для поиска и вывода контроллеров в указанном модуле/пакете.
 * Можно использовать для диагностики проблем с поиском контроллеров.
 */
public class ControllerFinder {

    private static final Logger log = LoggerFactory.getLogger(ControllerFinder.class);

    /**
     * Находит все контроллеры в указанном пакете
     * 
     * @param packageName имя пакета для поиска (например, "com.example.moduleA.controller")
     * @return список найденных контроллеров
     */
    public static List<ControllerInfo> findControllers(String packageName) {
        List<ControllerInfo> controllers = new ArrayList<>();
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            
            String packagePath = packageName.replace('.', '/');
            String pattern = "classpath*:" + packagePath + "/**/*.class";
            
            System.out.println("========================================");
            System.out.println("Поиск контроллеров в пакете: " + packageName);
            System.out.println("Паттерн поиска: " + pattern);
            System.out.println("========================================");
            
            Resource[] resources = resolver.getResources(pattern);
            System.out.println("Найдено ресурсов: " + resources.length);
            
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    try {
                        MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                        String className = metadataReader.getClassMetadata().getClassName();
                        
                        System.out.println("Проверка класса: " + className);
                        
                        if (metadataReader.getAnnotationMetadata().hasAnnotation(RestController.class.getName())) {
                            try {
                                Class<?> clazz = Class.forName(className);
                                ControllerInfo info = new ControllerInfo();
                                info.className = className;
                                info.simpleName = clazz.getSimpleName();
                                info.packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
                                info.resourceLocation = resource.getDescription();
                                controllers.add(info);
                                
                                System.out.println("✓ НАЙДЕН КОНТРОЛЛЕР: " + className);
                            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                                System.out.println("✗ Не удалось загрузить класс: " + className + " - " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("✗ Ошибка при чтении ресурса: " + resource + " - " + e.getMessage());
                    }
                }
            }
            
            System.out.println("========================================");
            System.out.println("ИТОГО найдено контроллеров: " + controllers.size());
            System.out.println("========================================");
            
            if (controllers.isEmpty()) {
                System.out.println("\n⚠ КОНТРОЛЛЕРЫ НЕ НАЙДЕНЫ!");
                System.out.println("Проверьте:");
                System.out.println("  1. Правильность имени пакета: " + packageName);
                System.out.println("  2. Наличие аннотации @RestController на классах");
                System.out.println("  3. Наличие модуля в classpath");
                System.out.println("  4. Компиляцию модуля (mvn clean compile)");
            } else {
                System.out.println("\nНайденные контроллеры:");
                for (int i = 0; i < controllers.size(); i++) {
                    ControllerInfo info = controllers.get(i);
                    System.out.println((i + 1) + ". " + info.className);
                    System.out.println("   Расположение: " + info.resourceLocation);
                }
            }
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при поиске контроллеров: " + e.getMessage());
            e.printStackTrace();
        }
        
        return controllers;
    }

    /**
     * Находит все контроллеры во всем classpath
     */
    public static List<ControllerInfo> findAllControllers() {
        List<ControllerInfo> controllers = new ArrayList<>();
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
            
            String pattern = "classpath*:**/*.class";
            
            System.out.println("========================================");
            System.out.println("Поиск ВСЕХ контроллеров в classpath");
            System.out.println("Паттерн поиска: " + pattern);
            System.out.println("========================================");
            
            Resource[] resources = resolver.getResources(pattern);
            System.out.println("Найдено ресурсов: " + resources.length);
            
            int checked = 0;
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    try {
                        MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                        String className = metadataReader.getClassMetadata().getClassName();
                        
                        // Пропускаем системные классы
                        if (className.startsWith("org.springframework") ||
                            className.startsWith("org.apache") ||
                            className.startsWith("java.") ||
                            className.startsWith("javax.") ||
                            className.startsWith("jakarta.") ||
                            className.contains("$")) {
                            continue;
                        }
                        
                        checked++;
                        
                        if (metadataReader.getAnnotationMetadata().hasAnnotation(RestController.class.getName())) {
                            try {
                                Class<?> clazz = Class.forName(className);
                                ControllerInfo info = new ControllerInfo();
                                info.className = className;
                                info.simpleName = clazz.getSimpleName();
                                info.packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
                                info.resourceLocation = resource.getDescription();
                                controllers.add(info);
                                
                                System.out.println("✓ НАЙДЕН КОНТРОЛЛЕР: " + className);
                            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                                // Игнорируем
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки чтения
                    }
                }
            }
            
            System.out.println("========================================");
            System.out.println("Проверено классов: " + checked);
            System.out.println("ИТОГО найдено контроллеров: " + controllers.size());
            System.out.println("========================================");
            
            if (!controllers.isEmpty()) {
                System.out.println("\nНайденные контроллеры:");
                for (int i = 0; i < controllers.size(); i++) {
                    ControllerInfo info = controllers.get(i);
                    System.out.println((i + 1) + ". " + info.className);
                    System.out.println("   Пакет: " + info.packageName);
                    System.out.println("   Расположение: " + info.resourceLocation);
                }
            }
            
        } catch (Exception e) {
            System.err.println("ОШИБКА при поиске контроллеров: " + e.getMessage());
            e.printStackTrace();
        }
        
        return controllers;
    }

    /**
     * Информация о найденном контроллере
     */
    public static class ControllerInfo {
        public String className;
        public String simpleName;
        public String packageName;
        public String resourceLocation;
        
        @Override
        public String toString() {
            return className + " (" + resourceLocation + ")";
        }
    }

    /**
     * Можно запустить как отдельное приложение для тестирования
     * java -cp ... ControllerFinder com.example.moduleA.controller
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Использование:");
            System.out.println("  java ControllerFinder <package-name>  - поиск в указанном пакете");
            System.out.println("  java ControllerFinder --all            - поиск всех контроллеров");
            System.out.println("\nПример:");
            System.out.println("  java ControllerFinder com.example.moduleA.controller");
            System.out.println("  java ControllerFinder --all");
            return;
        }
        
        if ("--all".equals(args[0])) {
            findAllControllers();
        } else {
            findControllers(args[0]);
        }
    }
}
