# Библиотека для авторизации на основе аннотаций

## Описание

Это библиотека для интеграции в другой модульный проект. Предоставляет механизм авторизации на основе аннотаций из контроллеров.

**ВАЖНО:** Этот проект предназначен для интеграции в другой проект, где уже настроена авторизация. Используйте `AnnotationBasedAuthorizationChecker` в вашей существующей SecurityConfig.

## Два модуля - два решения

Проект предоставляет два модуля с разными подходами к сканированию эндпоинтов:

### 1. `gateway` - Обычное сканирование (при старте)
- Сканирует все контроллеры при старте приложения
- Все эндпоинты регистрируются заранее
- Быстрая проверка авторизации (правила уже в реестре)
- Медленнее старт приложения

### 2. `gateway_lazy` - Ленивое сканирование (по требованию)
- Не сканирует контроллеры при старте
- Эндпоинты сканируются при первом обращении к ним
- Быстрый старт приложения
- Первое обращение к эндпоинту медленнее (нужно отсканировать)

**Выберите модуль в зависимости от ваших требований:**
- Используйте `gateway` если у вас мало контроллеров и нужна максимальная производительность
- Используйте `gateway_lazy` если у вас много контроллеров и нужен быстрый старт

## Структура проекта

```
security2/
├── pom.xml                    # Родительский POM
├── commons/                   # Общий модуль
│   ├── pom.xml
│   └── src/main/java/com/example/commons/
│       └── security/
│           ├── annotation/    # Кастомные аннотации безопасности
│           │   ├── RequireReadDeclaration.java
│           │   ├── RequireWriteDeclaration.java
│           │   ├── RequireApproveDeclaration.java
│           │   ├── RequireReadWare.java
│           │   ├── RequireWriteWare.java
│           │   └── RequireManageInventory.java
│           └── service/
│               └── UserService.java  # Сервис для управления пользователями
├── gateway/                   # Модуль с обычным сканированием (при старте)
│   ├── pom.xml
│   └── src/main/java/com/example/gateway/
│       ├── config/
│       │   ├── ControllerScanner.java              # Сканер контроллеров (для монолита)
│       │   ├── RemoteModuleScanner.java            # Сканер удаленных модулей (для микросервисов)
│       │   ├── EndpointRegistrationService.java   # Сервис регистрации эндпоинтов
│       │   ├── EndpointRegistrationController.java # REST API для регистрации эндпоинтов
│       │   ├── ControllerFinder.java               # Утилита для диагностики поиска контроллеров
│       │   ├── ControllerDiagnosticController.java # REST API для диагностики
│       │   ├── EndpointAuthorizationRegistry.java  # Реестр правил авторизации
│       │   └── AutoRescanService.java              # Автоматическое пересканирование
│       └── security/
│           ├── AnnotationBasedAuthorizationChecker.java  # Компонент для интеграции
│           └── CustomAuthorizationManager.java            # Методы проверки авторизации
└── gateway_lazy/              # Модуль с ленивым сканированием (по требованию)
    ├── pom.xml
    └── src/main/java/com/example/gateway_lazy/
        ├── config/
        │   ├── LazyEndpointScanner.java            # Ленивый сканер эндпоинтов
        │   └── EndpointAuthorizationRegistry.java  # Реестр правил авторизации
        └── security/
            ├── AnnotationBasedAuthorizationChecker.java  # Компонент для интеграции (с ленивым сканированием)
            └── CustomAuthorizationManager.java            # Методы проверки авторизации
```

## Основные компоненты

### 1. ControllerScanner (для монолита)
- Сканирует контроллеры из указанных пакетов при старте приложения
- Находит аннотации безопасности на методах контроллеров
- Создает маппинг эндпоинт → метод CustomAuthorizationManager
- **Используется когда все модули в одном classpath**

### 1a. RemoteModuleScanner (для микросервисов)
- Сканирует удаленные модули через HTTP API
- Получает список эндпоинтов от каждого модуля
- Регистрирует эндпоинты в EndpointAuthorizationRegistry
- **Используется когда каждый модуль в отдельном контейнере**

### 2. EndpointAuthorizationRegistry
- Хранит маппинг путь+метод → метод авторизации
- Поддерживает path variables через паттерны

### 3. CustomAuthorizationManager
- Содержит методы для проверки различных типов доступа
- `checkReadDeclaration()`, `checkWriteDeclaration()`, `checkApproveDeclaration()`
- `checkReadWare()`, `checkWriteWare()`, `checkManageInventory()`

### 4. AnnotationBasedAuthorizationChecker
- **Главный компонент для интеграции**
- Используйте в вашей SecurityConfig: `.access(authorizationChecker::checkAuthorization)`
- Полный цикл проверки: путь → реестр → CustomAuthorizationManager → решение

### 5. ControllerFinder (диагностика)
- **Утилитный класс для поиска контроллеров в указанном модуле**
- Можно использовать для диагностики проблем с поиском контроллеров
- Выводит подробную информацию о найденных контроллерах
- Использование: `ControllerFinder.findControllers("com.example.module.controller")`

### 6. AutoRescanService (опционально, только в gateway)
- Автоматически пересканирует контроллеры при изменениях
- Работает в фоновом режиме с настраиваемым интервалом

### 7. LazyEndpointScanner (только в gateway_lazy)
- **Ленивое сканирование эндпоинтов** - сканирует конкретный эндпоинт при первом обращении к нему
- Ускоряет старт приложения (не сканирует все контроллеры при старте)
- Кэширует результаты для быстрого доступа
- Автоматически используется в модуле `gateway_lazy`

### 8. Кастомные аннотации (commons модуль)
- `@RequireReadDeclaration` - проверка права чтения деклараций
- `@RequireWriteDeclaration` - проверка права записи деклараций
- `@RequireApproveDeclaration` - проверка права утверждения деклараций
- `@RequireReadWare` - проверка права чтения товаров
- `@RequireWriteWare` - проверка права записи товаров
- `@RequireManageInventory` - проверка права управления инвентарем

## Быстрый старт

### 1. Добавьте зависимость

В `pom.xml` вашего проекта:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>gateway</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Настройте пакеты для сканирования

В `application.properties` вашего проекта:

```properties
# Пакеты для сканирования контроллеров (разделенные запятой)
endpoint-scanner.scan-packages=com.yourproject.module1.controller,com.yourproject.module2.controller
```

### 3. Интегрируйте в вашу SecurityConfig

```java
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class YourSecurityConfig {
    
    private final AnnotationBasedAuthorizationChecker authorizationChecker;
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            // Ваша существующая конфигурация аутентификации...
            .authorizeExchange(exchanges -> exchanges
                // Используйте AnnotationBasedAuthorizationChecker для проверки авторизации
                .pathMatchers("/api/**")
                    .access(authorizationChecker::checkAuthorization)
                
                // Остальные ваши правила...
                .anyExchange().authenticated()
            );
        
        return http.build();
    }
}
```

### 4. Используйте аннотации в контроллерах

```java
@RestController
@RequestMapping("/api/your-module/resource")
public class YourController {

    @GetMapping
    @RequireReadDeclaration  // Автоматически проверяется через CustomAuthorizationManager
    public ResponseEntity<List<Resource>> getAll() {
        // ...
    }

    @PostMapping
    @RequireWriteDeclaration  // Автоматически проверяется через CustomAuthorizationManager
    public ResponseEntity<Resource> create(@RequestBody Resource resource) {
        // ...
    }
}
```

## Как это работает

### Полный цикл проверки (все в этом проекте):

```
1. ЗАПРОС ПРИХОДИТ:
   GET /api/your-module/resource
   Header: X-User-Id: user1

2. ВАША SECURITYCONFIG:
   → Проверяет аутентификацию (ваша логика)
   → Вызывает authorizationChecker.checkAuthorization()

3. ANNOTATIONBASEDAUTHORIZATIONCHECKER:
   → Определяет путь и метод: "GET:/api/your-module/resource"
   → Ищет в EndpointAuthorizationRegistry

4. ENDPOINTAUTHORIZATIONREGISTRY:
   → Находит: "GET:/api/your-module/resource" → checkReadDeclaration()

5. CUSTOMAUTHORIZATIONMANAGER:
   → Вызывает checkReadDeclaration()
   → Проверяет authorities: [READ_DECLARATION, ADMIN]
   → Возвращает AuthorizationDecision

6. РЕЗУЛЬТАТ:
   ✅ Разрешить доступ / ❌ Запретить доступ
```

## Автоматическое пересканирование

При обновлении модулей контроллеры автоматически пересканируются (если включено):

```properties
gateway.auto-rescan.enabled=true
gateway.auto-rescan.polling-interval=30000
gateway.auto-rescan.initial-delay=30000
```

## Кастомизация

### Добавление новых типов доступа:

1. Создайте новую аннотацию в `commons` модуле:
```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireCustomAccess {
}
```

2. Добавьте метод в `CustomAuthorizationManager`:
```java
public Mono<AuthorizationDecision> checkCustomAccess(
        Mono<Authentication> authenticationMono,
        AuthorizationContext context) {
    return checkAuthorities(authenticationMono, Set.of("CUSTOM_ACCESS", "ADMIN"), 
            "custom access");
}
```

3. Добавьте маппинг в `ControllerScanner.findAuthorizationMethod()`:
```java
if (AnnotationUtils.findAnnotation(method, RequireCustomAccess.class) != null) {
    return authorizationManager::checkCustomAccess;
}
```

## Сборка проекта

```bash
mvn clean install
```

## Документация

- **[INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md)** - Интеграция как библиотека (через Maven dependency)
- **[INTEGRATION_WITHOUT_LIBRARY.md](INTEGRATION_WITHOUT_LIBRARY.md)** - Интеграция без библиотеки (копирование модулей/классов)
- **[FILES_TO_COPY.md](FILES_TO_COPY.md)** - Список файлов для копирования
- **[CLASSPATH_GUIDE.md](CLASSPATH_GUIDE.md)** - Как убедиться, что модули с контроллерами в classpath
- **[CONTROLLER_FINDER_GUIDE.md](CONTROLLER_FINDER_GUIDE.md)** - Руководство по использованию ControllerFinder для диагностики
- **[MICROSERVICES_GUIDE.md](MICROSERVICES_GUIDE.md)** - **Использование в микросервисной архитектуре (модули в отдельных контейнерах)**
- **[LAZY_SCANNING_GUIDE.md](LAZY_SCANNING_GUIDE.md)** - **Ленивое сканирование эндпоинтов (модуль gateway_lazy)**
- **[LAZY_SCANNING_GUIDE.md](LAZY_SCANNING_GUIDE.md)** - **Ленивое сканирование эндпоинтов (по требованию)**

## Принципы проектирования

- **DRY** - централизованная логика авторизации
- **SOLID** - каждый компонент имеет одну ответственность
- **Расширяемость** - легко добавлять новые типы доступа через аннотации
- **Интегрируемость** - работает с существующей SecurityConfig другого проекта
