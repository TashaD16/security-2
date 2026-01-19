# Интеграция без использования библиотеки

Пошаговая инструкция по внедрению проекта в ваш проект без использования Maven dependency.

---

## 🎯 Вариант 1: Копирование модулей (РЕКОМЕНДУЕТСЯ)

### Шаг 1: Скопируйте модули

Скопируйте папки `commons` и `gateway` из этого проекта в корень вашего проекта:

```
ваш-проект/
├── commons/                    ← Скопируйте из security2/commons
│   ├── pom.xml
│   └── src/main/java/com/example/commons/
│       └── security/
│           ├── annotation/     ← 6 аннотаций
│           └── service/        ← UserService
│
└── gateway/                    ← Скопируйте из security2/gateway
    ├── pom.xml
    └── src/main/java/com/example/gateway/
        ├── config/             ← 4 класса конфигурации
        └── security/           ← 2 класса безопасности
```

### Шаг 2: Добавьте модули в родительский pom.xml

Откройте ваш родительский `pom.xml` и добавьте модули в секцию `<modules>`:

```xml
<project>
    <modules>
        <!-- Ваши существующие модули -->
        <module>your-module1</module>
        <module>your-module2</module>
        
        <!-- Добавьте скопированные модули -->
        <module>commons</module>
        <module>gateway</module>
    </modules>
</project>
```

### Шаг 3: Добавьте зависимости

#### В модулях, где используются аннотации:

Откройте `pom.xml` ваших бизнес-модулей и добавьте:

```xml
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>commons</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

#### В модуле с SecurityConfig:

Откройте `pom.xml` модуля, где находится ваша `SecurityConfig`, и добавьте:

```xml
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>gateway</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

### Шаг 4: Настройте application.properties

Откройте `application.properties` вашего проекта и добавьте:

```properties
# Пакеты для сканирования контроллеров (разделенные запятой)
# Укажите пакеты, где находятся ваши контроллеры
endpoint-scanner.scan-packages=com.yourproject.module1.controller,com.yourproject.module2.controller

# Настройки автоматического пересканирования (опционально)
gateway.auto-rescan.enabled=true
gateway.auto-rescan.polling-interval=30000
gateway.auto-rescan.initial-delay=30000
```

### Шаг 5: Интегрируйте в SecurityConfig

Откройте вашу `SecurityConfig` и добавьте:

```java
package com.yourproject.config;

import com.example.gateway.security.AnnotationBasedAuthorizationChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

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
                // Публичные эндпоинты
                .pathMatchers("/public/**", "/actuator/health")
                    .permitAll()
                
                // API эндпоинты - авторизация через аннотации
                .pathMatchers("/api/**")
                    .access(authorizationChecker::checkAuthorization)
                
                // Все остальные требуют аутентификации
                .anyExchange()
                    .authenticated()
            );
        
        return http.build();
    }
}
```

### Шаг 6: Используйте аннотации в контроллерах

В ваших контроллерах добавьте аннотации:

```java
package com.yourproject.module1.controller;

import com.example.commons.security.annotation.RequireReadDeclaration;
import com.example.commons.security.annotation.RequireWriteDeclaration;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/declarations")
public class DeclarationController {

    @GetMapping
    @RequireReadDeclaration  // ← Автоматически проверяется
    public ResponseEntity<List<Declaration>> getAll() {
        // ...
    }

    @PostMapping
    @RequireWriteDeclaration  // ← Автоматически проверяется
    public ResponseEntity<Declaration> create(@RequestBody Declaration dto) {
        // ...
    }
}
```

### ✅ Готово!

После выполнения всех шагов:
1. Соберите проект: `mvn clean install`
2. Запустите приложение
3. `ControllerScanner` автоматически отсканирует ваши контроллеры при старте
4. Авторизация будет работать через аннотации

---

## 🔧 Вариант 2: Копирование только классов

Если вы не хотите создавать отдельные модули, скопируйте только необходимые классы.

### Шаг 1: Создайте структуру пакетов

Создайте в вашем проекте следующую структуру:

```
ваш-проект/src/main/java/com/yourproject/
└── security/
    ├── annotation/          ← Сюда скопируйте аннотации
    ├── service/             ← Сюда скопируйте UserService
    ├── config/              ← Сюда скопируйте классы конфигурации
    └── authorization/       ← Сюда скопируйте классы авторизации
```

### Шаг 2: Скопируйте файлы

#### Аннотации (6 файлов):
```
commons/src/main/java/com/example/commons/security/annotation/
├── RequireApproveDeclaration.java
├── RequireManageInventory.java
├── RequireReadDeclaration.java
├── RequireReadWare.java
├── RequireWriteDeclaration.java
└── RequireWriteWare.java
```
→ Скопируйте в: `ваш-проект/src/main/java/com/yourproject/security/annotation/`

#### Сервисы (1 файл):
```
commons/src/main/java/com/example/commons/security/service/UserService.java
```
→ Скопируйте в: `ваш-проект/src/main/java/com/yourproject/security/service/`

#### Конфигурация (4 файла):
```
gateway/src/main/java/com/example/gateway/config/
├── ControllerScanner.java
├── EndpointAuthorizationRegistry.java
├── AutoRescanService.java          (опционально)
└── SchedulingConfig.java            (опционально)
```
→ Скопируйте в: `ваш-проект/src/main/java/com/yourproject/security/config/`

#### Безопасность (2 файла):
```
gateway/src/main/java/com/example/gateway/security/
├── AnnotationBasedAuthorizationChecker.java
└── CustomAuthorizationManager.java
```
→ Скопируйте в: `ваш-проект/src/main/java/com/yourproject/security/authorization/`

### Шаг 3: Обновите package declarations

Во всех скопированных файлах замените package:

**Было:**
```java
package com.example.commons.security.annotation;
package com.example.gateway.config;
package com.example.gateway.security;
```

**Стало:**
```java
package com.yourproject.security.annotation;
package com.yourproject.security.config;
package com.yourproject.security.authorization;
```

### Шаг 4: Обновите импорты

Во всех скопированных файлах замените импорты:

**Было:**
```java
import com.example.commons.security.annotation.RequireReadDeclaration;
import com.example.gateway.security.CustomAuthorizationManager;
import com.example.gateway.config.EndpointAuthorizationRegistry;
```

**Стало:**
```java
import com.yourproject.security.annotation.RequireReadDeclaration;
import com.yourproject.security.authorization.CustomAuthorizationManager;
import com.yourproject.security.config.EndpointAuthorizationRegistry;
```

### Шаг 5: Добавьте зависимости в pom.xml

В `pom.xml` вашего проекта добавьте:

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

### Шаг 6: Настройте и используйте

Выполните **Шаги 4-6** из Варианта 1, но используйте обновленные импорты:

```java
import com.yourproject.security.authorization.AnnotationBasedAuthorizationChecker;
import com.yourproject.security.annotation.RequireReadDeclaration;
```

---

## 📋 Список всех файлов для копирования

### Модуль commons (7 файлов):
- `commons/pom.xml`
- `commons/src/main/java/com/example/commons/security/annotation/RequireApproveDeclaration.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireManageInventory.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireReadDeclaration.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireReadWare.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireWriteDeclaration.java`
- `commons/src/main/java/com/example/commons/security/annotation/RequireWriteWare.java`
- `commons/src/main/java/com/example/commons/security/service/UserService.java`

### Модуль gateway (7 файлов):
- `gateway/pom.xml`
- `gateway/src/main/java/com/example/gateway/config/ControllerScanner.java`
- `gateway/src/main/java/com/example/gateway/config/EndpointAuthorizationRegistry.java`
- `gateway/src/main/java/com/example/gateway/config/AutoRescanService.java` (опционально)
- `gateway/src/main/java/com/example/gateway/config/SchedulingConfig.java` (опционально)
- `gateway/src/main/java/com/example/gateway/security/AnnotationBasedAuthorizationChecker.java`
- `gateway/src/main/java/com/example/gateway/security/CustomAuthorizationManager.java`
- `gateway/src/main/resources/application.properties` (настройки)

---

## ⚙️ Настройки application.properties

### Обязательные настройки:

```properties
# Пакеты для сканирования контроллеров
endpoint-scanner.scan-packages=com.yourproject.module1.controller,com.yourproject.module2.controller
```

### Опциональные настройки:

```properties
# Автоматическое пересканирование при изменении файлов
gateway.auto-rescan.enabled=true
gateway.auto-rescan.polling-interval=30000      # Интервал проверки (мс)
gateway.auto-rescan.initial-delay=30000         # Задержка перед первой проверкой (мс)

# Логирование (для отладки)
logging.level.com.example.gateway=DEBUG
logging.level.org.springframework.security=DEBUG
```

---

## 🔍 Как это работает

### Полный цикл проверки авторизации:

```
1. ЗАПРОС:
   GET /api/declarations
   Header: Authorization: Bearer <token>

2. ВАША SECURITYCONFIG:
   → Проверяет аутентификацию (ваша логика)
   → Вызывает authorizationChecker.checkAuthorization()

3. ANNOTATIONBASEDAUTHORIZATIONCHECKER:
   → Определяет путь и метод: "GET:/api/declarations"
   → Ищет в EndpointAuthorizationRegistry

4. ENDPOINTAUTHORIZATIONREGISTRY:
   → Находит: "GET:/api/declarations" → checkReadDeclaration()

5. CUSTOMAUTHORIZATIONMANAGER:
   → Вызывает checkReadDeclaration()
   → Проверяет authorities: [READ_DECLARATION, ADMIN]
   → Возвращает AuthorizationDecision

6. РЕЗУЛЬТАТ:
   ✅ Разрешить доступ / ❌ Запретить доступ
```

### Сканирование контроллеров:

1. При старте приложения `ControllerScanner` (реализует `CommandLineRunner`) автоматически сканирует контроллеры
2. Находит все методы с аннотациями безопасности
3. Создает маппинг: `HTTP_METHOD:путь` → `метод CustomAuthorizationManager`
4. Сохраняет в `EndpointAuthorizationRegistry`

---

## 🛠️ Кастомизация

### Добавление нового типа доступа:

#### 1. Создайте новую аннотацию:

```java
package com.yourproject.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireCustomAccess {
}
```

#### 2. Добавьте метод в CustomAuthorizationManager:

```java
public Mono<AuthorizationDecision> checkCustomAccess(
        Mono<Authentication> authenticationMono,
        AuthorizationContext context) {
    return checkAuthorities(authenticationMono, 
            Set.of("CUSTOM_ACCESS", "ADMIN"), 
            "custom access");
}
```

#### 3. Добавьте маппинг в ControllerScanner:

В методе `findAuthorizationMethod()` добавьте:

```java
if (AnnotationUtils.findAnnotation(method, RequireCustomAccess.class) != null) {
    return authorizationManager::checkCustomAccess;
}
```

---

## ❓ Часто задаваемые вопросы

### Q: Нужно ли изменять UserService?

**A:** Если у вас уже есть свой сервис пользователей, можете адаптировать `CustomAuthorizationManager` для использования вашего сервиса. `UserService` в этом проекте используется только для демонстрации.

### Q: Можно ли отключить автоматическое пересканирование?

**A:** Да, установите в `application.properties`:
```properties
gateway.auto-rescan.enabled=false
```

### Q: Что делать, если контроллеры не сканируются?

**A:** Проверьте:
1. Правильно ли указаны пакеты в `endpoint-scanner.scan-packages`
2. Есть ли аннотация `@RestController` на контроллерах
3. Есть ли аннотации безопасности на методах
4. Логи при старте приложения (должно быть сообщение о количестве отсканированных контроллеров)

### Q: Можно ли использовать с Spring MVC (не WebFlux)?

**A:** Компоненты разработаны для Spring WebFlux (реактивная безопасность). Для Spring MVC потребуется адаптация классов.

---

## ✅ Чек-лист интеграции

- [ ] Скопированы модули `commons` и `gateway` (или классы)
- [ ] Модули добавлены в родительский `pom.xml`
- [ ] Добавлены зависимости в `pom.xml` модулей
- [ ] Настроены пакеты для сканирования в `application.properties`
- [ ] `AnnotationBasedAuthorizationChecker` добавлен в `SecurityConfig`
- [ ] Аннотации добавлены в контроллеры
- [ ] Проект собран: `mvn clean install`
- [ ] Приложение запущено и контроллеры отсканированы
- [ ] Авторизация работает корректно

---

## 📚 Дополнительные материалы

- [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) - Интеграция как библиотека
- [FILES_TO_COPY.md](FILES_TO_COPY.md) - Детальный список файлов
- [README.md](README.md) - Общее описание проекта
