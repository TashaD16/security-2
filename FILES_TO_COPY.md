# Список файлов для копирования

## Вариант 1: Копирование модулей (РЕКОМЕНДУЕТСЯ)

### Модуль commons:
```
commons/
├── pom.xml
└── src/main/java/com/example/commons/
    └── security/
        ├── annotation/
        │   ├── RequireApproveDeclaration.java
        │   ├── RequireManageInventory.java
        │   ├── RequireReadDeclaration.java
        │   ├── RequireReadWare.java
        │   ├── RequireWriteDeclaration.java
        │   └── RequireWriteWare.java
        └── service/
            └── UserService.java
```

### Модуль gateway:
```
gateway/
├── pom.xml
└── src/main/java/com/example/gateway/
    ├── config/
    │   ├── AutoRescanService.java
    │   ├── ControllerScanner.java
    │   ├── EndpointAuthorizationRegistry.java
    │   └── SchedulingConfig.java
    └── security/
        ├── AnnotationBasedAuthorizationChecker.java
        └── CustomAuthorizationManager.java
```

### Конфигурация:
```
gateway/src/main/resources/application.properties
```

---

## Вариант 2: Копирование только классов

### Необходимые файлы:

#### Аннотации (6 файлов):
- `commons/src/main/java/com/example/commons/security/annotation/RequireApproveDeclaration.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireManageInventory.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireReadDeclaration.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireReadWare.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireWriteDeclaration.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireWriteWare.java`

#### Сервисы (1 файл):
- `commons/src/main/java/com/example/commons/security/service/UserService.java`

#### Конфигурация (4 файла):
- `gateway/src/main/java/com/example/gateway/config/ControllerScanner.java`
- `gateway/src/main/java/com/example/gateway/config/EndpointAuthorizationRegistry.java`
- `gateway/src/main/java/com/example/gateway/config/AutoRescanService.java` (опционально)
- `gateway/src/main/java/com/example/gateway/config/SchedulingConfig.java` (опционально, если используете AutoRescanService)

#### Безопасность (2 файла):
- `gateway/src/main/java/com/example/gateway/security/AnnotationBasedAuthorizationChecker.java`
- `gateway/src/main/java/com/example/gateway/security/CustomAuthorizationManager.java`

**Всего: 13 файлов (или 11 без опциональных)**

---

## После копирования

### 1. Обновите package declarations

Замените во всех файлах:
```java
package com.example.gateway... → package com.yourproject.security...
package com.example.commons... → package com.yourproject.security...
```

### 2. Обновите импорты

Замените во всех файлах:
```java
import com.example.gateway... → import com.yourproject.security...
import com.example.commons... → import com.yourproject.security...
```

### 3. Добавьте зависимости в pom.xml

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 4. Настройте application.properties

```properties
endpoint-scanner.scan-packages=com.yourproject.module1.controller,com.yourproject.module2.controller
gateway.auto-rescan.enabled=true
```

### 5. Используйте в SecurityConfig

```java
@RequiredArgsConstructor
public class YourSecurityConfig {
    private final AnnotationBasedAuthorizationChecker authorizationChecker;
    
    // ...
    .pathMatchers("/api/**")
        .access(authorizationChecker::checkAuthorization)
}
```
