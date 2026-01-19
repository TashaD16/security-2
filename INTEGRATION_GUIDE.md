# Руководство по интеграции в другой проект

Этот проект предназначен для интеграции в другой модульный проект, где уже настроена авторизация.

## Что предоставляет этот проект

### Основные компоненты:

1. **ControllerScanner** - сканирует контроллеры и находит аннотации безопасности
2. **EndpointAuthorizationRegistry** - хранит маппинг эндпоинт → метод авторизации
3. **CustomAuthorizationManager** - содержит методы для проверки различных типов доступа
4. **AnnotationBasedAuthorizationChecker** - компонент для проверки авторизации (используйте в вашей SecurityConfig)

### Аннотации (commons модуль):

- `@RequireReadDeclaration`
- `@RequireWriteDeclaration`
- `@RequireApproveDeclaration`
- `@RequireReadWare`
- `@RequireWriteWare`
- `@RequireManageInventory`

## Интеграция в существующий проект

### Шаг 1: Добавьте зависимость

В `pom.xml` вашего проекта:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>gateway</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Шаг 2: Настройте сканирование контроллеров

В `application.properties` вашего проекта:

```properties
# Пакеты для сканирования контроллеров (разделенные запятой)
endpoint-scanner.scan-packages=com.yourproject.module1.controller,com.yourproject.module2.controller
```

Или оставьте по умолчанию (сканирует `com.example.moduleA.controller` и `com.example.moduleB.controller`).

### Шаг 3: Интегрируйте в вашу SecurityConfig

В вашей существующей `SecurityConfig`:

```java
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class YourSecurityConfig {

    private final AnnotationBasedAuthorizationChecker authorizationChecker;
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            // Ваша существующая конфигурация...
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

### Шаг 4: Используйте аннотации в контроллерах

В ваших контроллерах просто добавьте аннотации:

```java
@RestController
@RequestMapping("/api/your-module/resource")
public class YourController {

    @GetMapping
    @RequireReadDeclaration  // Автоматически будет проверено через CustomAuthorizationManager
    public ResponseEntity<List<Resource>> getAll() {
        // ...
    }

    @PostMapping
    @RequireWriteDeclaration  // Автоматически будет проверено через CustomAuthorizationManager
    public ResponseEntity<Resource> create(@RequestBody Resource resource) {
        // ...
    }
}
```

## Как это работает

### Полный цикл проверки:

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

## Важно

- **SecurityConfig в этом проекте** - только для справки/сравнения
- **Используйте AnnotationBasedAuthorizationChecker** в вашей SecurityConfig
- **ControllerScanner** автоматически сканирует контроллеры при старте
- **Вся логика проверки** находится в этом проекте (CustomAuthorizationManager)
