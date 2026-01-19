# Многомодульное Spring Boot приложение с централизованной безопасностью

## Описание

Это многомодульное Spring Boot приложение с централизованной логикой авторизации в модуле **gateway**. Приложение состоит из четырех модулей:

- **commons** - общий модуль с кастомными аннотациями безопасности
- **gateway** (порт 8082) - централизованный модуль безопасности
- **moduleA** (порт 8080) - бизнес-модуль для работы с декларациями
- **moduleB** (порт 8081) - бизнес-модуль для работы с товарами

## Архитектура безопасности

### Принципы

1. **Единая точка входа**: Gateway обрабатывает все запросы и применяет правила безопасности
2. **Централизованная конфигурация**: Все правила авторизации определены в `gateway/src/main/java/com/example/gateway/config/SecurityConfig.java`
3. **Упрощенная архитектура**: Бизнес-модули не содержат сложной конфигурации безопасности и сосредоточены на бизнес-логике
4. **Легкое обслуживание**: Изменения в правилах безопасности требуют изменений только в gateway модуле
5. **Кастомные аннотации**: Использование переиспользуемых мета-аннотаций из модуля commons для проверки прав доступа

### Компоненты безопасности

#### CustomAuthorizationManager
- Извлекает переменные пути (declarationId, wareId) из URI
- Проверяет права доступа на основе ролей и authorities
- Выполняет проверку владения ресурсами
- Логирует все операции авторизации

#### Кастомные аннотации (commons модуль)
- `@RequireReadDeclaration` - проверка права чтения деклараций
- `@RequireWriteDeclaration` - проверка права записи деклараций
- `@RequireApproveDeclaration` - проверка права утверждения деклараций
- `@RequireReadWare` - проверка права чтения товаров
- `@RequireWriteWare` - проверка права записи товаров
- `@RequireManageInventory` - проверка права управления инвентарем
- Все аннотации централизованы в модуле commons для переиспользования

## Структура проекта

```
security2/
├── pom.xml                    # Родительский POM
├── commons/                   # Общий модуль
│   ├── pom.xml
│   └── src/main/java/com/example/commons/
│       └── security/annotation/
│           ├── RequireReadDeclaration.java
│           ├── RequireWriteDeclaration.java
│           ├── RequireApproveDeclaration.java
│           ├── RequireReadWare.java
│           ├── RequireWriteWare.java
│           └── RequireManageInventory.java
├── gateway/                   # Модуль Gateway
│   ├── pom.xml
│   └── src/main/java/com/example/gateway/
│       ├── GatewayApplication.java
│       ├── config/
│       │   └── SecurityConfig.java          # Централизованная конфигурация безопасности
│       └── security/
│           ├── CustomAuthorizationManager.java
│           ├── AuthenticatedUser.java
│           ├── UserPermissionService.java
│           ├── ResourceOwnershipService.java
│           ├── JwtAuthenticationConverter.java
│           └── UserService.java
├── moduleA/                   # Бизнес-модуль A
│   ├── pom.xml
│   └── src/main/java/com/example/moduleA/
│       ├── ModuleAApplication.java
│       ├── config/
│       │   └── SecurityConfig.java          # Минимальная конфигурация для @PreAuthorize
│       ├── controller/
│       │   └── DeclarationController.java
│       ├── service/
│       │   └── DeclarationService.java
│       └── dto/
│           └── DeclarationDto.java
└── moduleB/                   # Бизнес-модуль B
    ├── pom.xml
    └── src/main/java/com/example/moduleB/
        ├── ModuleBApplication.java
        ├── config/
        │   └── SecurityConfig.java          # Минимальная конфигурация для @PreAuthorize
        ├── controller/
        │   └── WareController.java
        ├── service/
        │   └── WareService.java
        └── dto/
            └── WareDto.java
```

## Тестовые пользователи

Приложение содержит следующие тестовые пользователи:

| User ID | Username | Roles | Authorities |
|---------|----------|-------|-------------|
| user1 | admin | ADMIN, USER | READ_DECLARATION, WRITE_DECLARATION, APPROVE_DECLARATION, READ_WARE, WRITE_WARE, MANAGE_INVENTORY, ADMIN |
| user2 | operator | USER | READ_DECLARATION, READ_WARE |
| user3 | moduleA_user | USER | READ_DECLARATION, WRITE_DECLARATION |
| user4 | moduleB_user | USER | READ_WARE, WRITE_WARE, MANAGE_INVENTORY |

## Права доступа

### Module A (Декларации)

- **GET /api/moduleA/declarations** - требует `READ_DECLARATION` или `ADMIN` (аннотация `@RequireReadDeclaration`)
- **GET /api/moduleA/declarations/{id}** - требует `READ_DECLARATION` или `ADMIN` (аннотация `@RequireReadDeclaration`)
- **POST /api/moduleA/declarations** - требует `WRITE_DECLARATION` или `ADMIN` (аннотация `@RequireWriteDeclaration`)
- **PUT /api/moduleA/declarations/{id}** - требует `WRITE_DECLARATION` или `ADMIN` (аннотация `@RequireWriteDeclaration`)
- **DELETE /api/moduleA/declarations/{id}** - требует `WRITE_DECLARATION` или `ADMIN` (аннотация `@RequireWriteDeclaration`)

### Module B (Товары)

- **GET /api/moduleB/wares** - требует `READ_WARE` или `ADMIN` (аннотация `@RequireReadWare`)
- **GET /api/moduleB/wares/{id}** - требует `READ_WARE` или `ADMIN` (аннотация `@RequireReadWare`)
- **POST /api/moduleB/wares** - требует `WRITE_WARE` или `ADMIN` (аннотация `@RequireWriteWare`)
- **PUT /api/moduleB/wares/{id}** - требует `WRITE_WARE` или `ADMIN` (аннотация `@RequireWriteWare`)
- **DELETE /api/moduleB/wares/{id}** - требует `WRITE_WARE` или `ADMIN` (аннотация `@RequireWriteWare`)

## Сборка и запуск

### Требования

- Java 17+
- Maven 3.6+

### Сборка проекта

```bash
mvn clean install
```

### Запуск модулей

Запустите каждый модуль отдельно:

```bash
# Terminal 1 - Gateway
cd gateway
mvn spring-boot:run

# Terminal 2 - Module A
cd moduleA
mvn spring-boot:run

# Terminal 3 - Module B
cd moduleB
mvn spring-boot:run
```

## Тестирование API

### Примеры запросов

#### Получение всех деклараций (требует аутентификации)

```bash
curl -X GET http://localhost:8082/api/moduleA/declarations \
  -H "Authorization: Bearer token" \
  -H "X-User-Id: user1"
```

#### Создание декларации

```bash
curl -X POST http://localhost:8082/api/moduleA/declarations \
  -H "Authorization: Bearer token" \
  -H "X-User-Id: user1" \
  -H "Content-Type: application/json" \
  -d '{
    "number": "DEC-003",
    "type": "IMPORT",
    "date": "2024-01-15T10:00:00",
    "description": "New declaration"
  }'
```

#### Получение всех товаров

```bash
curl -X GET http://localhost:8082/api/moduleB/wares \
  -H "Authorization: Bearer token" \
  -H "X-User-Id: user1"
```

## Принципы проектирования

### DRY (Don't Repeat Yourself)
- Централизованная логика безопасности в gateway модуле
- Переиспользование компонентов безопасности
- Единая точка конфигурации

### SOLID

- **Single Responsibility Principle**: Каждый класс имеет одну ответственность
  - `CustomAuthorizationManager` - только авторизация
  - `UserPermissionService` - только проверка прав
  - `ResourceOwnershipService` - только проверка владения

- **Open/Closed Principle**: Легко расширять новыми правилами авторизации без изменения существующего кода

- **Liskov Substitution Principle**: Использование интерфейсов Spring Security

- **Interface Segregation Principle**: Разделение ответственности между сервисами

- **Dependency Inversion Principle**: Зависимость от абстракций (интерфейсов Spring Security)

## Расширение функциональности

### Добавление нового модуля

1. Создайте новый модуль в корне проекта
2. Добавьте модуль в родительский `pom.xml`
3. Добавьте правила авторизации в `UserPermissionService`
4. Добавьте правила в `SecurityConfig` в gateway модуле

### Добавление новых прав доступа

1. Добавьте новое authority в `UserService`
2. Добавьте проверку в `UserPermissionService`
3. Используйте новое authority в `@PreAuthorize` аннотациях

## Примечания

- В демонстрационных целях используется упрощенная аутентификация через заголовок `X-User-Id`
- В production окружении необходимо реализовать полноценную JWT аутентификацию
- Хранение пользователей и ресурсов реализовано в памяти для демонстрации
- В production окружении необходимо использовать базу данных
