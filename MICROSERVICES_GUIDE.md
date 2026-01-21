# Руководство по использованию в микросервисной архитектуре

## Описание

Когда каждый модуль находится в отдельном контейнере с отдельным портом (микросервисная архитектура), gateway не может напрямую сканировать контроллеры через classpath. В этом случае используется механизм регистрации эндпоинтов через HTTP API.

## Архитектура

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   ModuleA   │         │   ModuleB   │         │   ModuleC   │
│  Port:8081  │         │  Port:8082  │         │  Port:8083  │
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
       │  Регистрация          │  Регистрация          │  Регистрация
       │  эндпоинтов           │  эндпоинтов           │  эндпоинтов
       └───────────────────────┴───────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │     Gateway      │
                    │   Port:8080      │
                    │                 │
                    │  - Регистрирует │
                    │    эндпоинты    │
                    │  - Проверяет    │
                    │    авторизацию  │
                    └─────────────────┘
```

## Настройка Gateway

### 1. Включите режим удаленных модулей

В `application.properties` gateway:

```properties
# Включить сканирование удаленных модулей
gateway.remote-modules.enabled=true

# URL модулей в формате: moduleName:http://host:port
gateway.remote-modules.urls=moduleA:http://localhost:8081,moduleB:http://localhost:8082,moduleC:http://localhost:8083

# Путь к endpoint для получения списка эндпоинтов модуля
gateway.remote-modules.endpoints=/api/module/endpoints

# Сканировать модули при старте приложения
gateway.remote-modules.scan-on-startup=true

# Количество попыток при ошибке подключения
gateway.remote-modules.retry-attempts=3

# Таймаут подключения в миллисекундах
gateway.remote-modules.timeout=5000
```

### 2. Gateway автоматически сканирует модули при старте

При запуске gateway будет:
1. Подключаться к каждому модулю по указанным URL
2. Вызывать `GET /api/module/endpoints` для получения списка эндпоинтов
3. Регистрировать эндпоинты в `EndpointAuthorizationRegistry`

## Настройка модулей (микросервисов)

### 1. Добавьте зависимость на commons

В `pom.xml` каждого модуля:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>commons</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Создайте контроллер для регистрации эндпоинтов

В каждом модуле создайте контроллер, наследующий `ModuleEndpointsController`:

```java
package com.yourproject.moduleA.controller;

import com.example.commons.security.controller.ModuleEndpointsController;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/module")
public class ModuleAEndpointsController extends ModuleEndpointsController {

    @Override
    protected String getModuleName() {
        return "moduleA";
    }

    @Override
    protected List<EndpointInfo> scanControllers() {
        List<EndpointInfo> endpoints = new ArrayList<>();
        
        // Сканируем контроллеры модуля
        scanController(DeclarationController.class, endpoints);
        // Добавьте другие контроллеры...
        
        return endpoints;
    }

    private void scanController(Class<?> controllerClass, List<EndpointInfo> endpoints) {
        RequestMapping classMapping = AnnotationUtils.findAnnotation(controllerClass, RequestMapping.class);
        String basePath = classMapping != null && classMapping.value().length > 0 
            ? classMapping.value()[0] 
            : "";

        for (Method method : controllerClass.getDeclaredMethods()) {
            String httpMethod = findHttpMethod(method);
            if (httpMethod == null) continue;

            String methodPath = findMethodPath(method);
            String fullPath = basePath + methodPath;
            String annotationType = findSecurityAnnotation(method);
            
            if (annotationType != null) {
                endpoints.add(new EndpointInfo(httpMethod, fullPath, annotationType));
            }
        }
    }

    private String findHttpMethod(Method method) {
        if (AnnotationUtils.findAnnotation(method, GetMapping.class) != null) return "GET";
        if (AnnotationUtils.findAnnotation(method, PostMapping.class) != null) return "POST";
        if (AnnotationUtils.findAnnotation(method, PutMapping.class) != null) return "PUT";
        if (AnnotationUtils.findAnnotation(method, DeleteMapping.class) != null) return "DELETE";
        if (AnnotationUtils.findAnnotation(method, PatchMapping.class) != null) return "PATCH";
        return null;
    }

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

    private String findSecurityAnnotation(Method method) {
        if (AnnotationUtils.findAnnotation(method, com.example.commons.security.annotation.RequireReadDeclaration.class) != null) {
            return "RequireReadDeclaration";
        }
        if (AnnotationUtils.findAnnotation(method, com.example.commons.security.annotation.RequireWriteDeclaration.class) != null) {
            return "RequireWriteDeclaration";
        }
        if (AnnotationUtils.findAnnotation(method, com.example.commons.security.annotation.RequireApproveDeclaration.class) != null) {
            return "RequireApproveDeclaration";
        }
        if (AnnotationUtils.findAnnotation(method, com.example.commons.security.annotation.RequireReadWare.class) != null) {
            return "RequireReadWare";
        }
        if (AnnotationUtils.findAnnotation(method, com.example.commons.security.annotation.RequireWriteWare.class) != null) {
            return "RequireWriteWare";
        }
        if (AnnotationUtils.findAnnotation(method, com.example.commons.security.annotation.RequireManageInventory.class) != null) {
            return "RequireManageInventory";
        }
        return null;
    }
}
```

### 3. Используйте аннотации в контроллерах модуля

```java
@RestController
@RequestMapping("/api/declarations")
public class DeclarationController {

    @GetMapping
    @RequireReadDeclaration
    public ResponseEntity<List<Declaration>> getAll() {
        // ...
    }

    @PostMapping
    @RequireWriteDeclaration
    public ResponseEntity<Declaration> create(@RequestBody Declaration declaration) {
        // ...
    }
}
```

## Альтернативный способ: Ручная регистрация через API

Если автоматическое сканирование не подходит, можно регистрировать эндпоинты вручную через API:

### Регистрация одного эндпоинта

```bash
POST http://gateway:8080/api/gateway/endpoints/register
Content-Type: application/json

{
  "moduleName": "moduleA",
  "httpMethod": "GET",
  "path": "/api/declarations",
  "annotationType": "RequireReadDeclaration"
}
```

### Регистрация нескольких эндпоинтов

```bash
POST http://gateway:8080/api/gateway/endpoints/register-batch
Content-Type: application/json

{
  "moduleName": "moduleA",
  "endpoints": [
    {
      "httpMethod": "GET",
      "path": "/api/declarations",
      "annotationType": "RequireReadDeclaration"
    },
    {
      "httpMethod": "POST",
      "path": "/api/declarations",
      "annotationType": "RequireWriteDeclaration"
    }
  ]
}
```

## Проверка регистрации

### Проверка через диагностический endpoint

```bash
# Получить все зарегистрированные эндпоинты
GET http://gateway:8080/diagnostic/find-all
```

### Проверка в логах

При успешной регистрации в логах gateway будет:

```
INFO - Scanning module moduleA at http://localhost:8081
INFO - ✓ Registered endpoint from module moduleA: GET:/api/declarations -> RequireReadDeclaration
INFO - Successfully registered 5/5 endpoints from module moduleA
```

## Решение проблем

### Проблема: Gateway не может подключиться к модулю

**Решение:**
1. Проверьте, что модуль запущен и доступен по указанному URL
2. Проверьте настройки `gateway.remote-modules.urls`
3. Проверьте сетевую доступность между контейнерами
4. Увеличьте `gateway.remote-modules.timeout` если модуль медленно стартует

### Проблема: Модуль не возвращает эндпоинты

**Решение:**
1. Проверьте, что контроллер `ModuleEndpointsController` создан и доступен
2. Проверьте путь `gateway.remote-modules.endpoints` (по умолчанию `/api/module/endpoints`)
3. Проверьте, что метод `scanControllers()` правильно реализован
4. Проверьте логи модуля на наличие ошибок

### Проблема: Эндпоинты не регистрируются

**Решение:**
1. Проверьте формат ответа от модуля (должен соответствовать `ModuleEndpointsResponse`)
2. Проверьте, что `annotationType` соответствует одному из поддерживаемых типов
3. Проверьте логи gateway на наличие ошибок регистрации

## Поддерживаемые типы аннотаций

- `RequireReadDeclaration`
- `RequireWriteDeclaration`
- `RequireApproveDeclaration`
- `RequireReadWare`
- `RequireWriteWare`
- `RequireManageInventory`

## Пример конфигурации Docker Compose

```yaml
version: '3.8'

services:
  gateway:
    image: your-gateway:latest
    ports:
      - "8080:8080"
    environment:
      - gateway.remote-modules.enabled=true
      - gateway.remote-modules.urls=moduleA:http://moduleA:8081,moduleB:http://moduleB:8082
      - gateway.remote-modules.scan-on-startup=true

  moduleA:
    image: your-moduleA:latest
    ports:
      - "8081:8081"
    depends_on:
      - gateway

  moduleB:
    image: your-moduleB:latest
    ports:
      - "8082:8082"
    depends_on:
      - gateway
```

## Сравнение подходов

| Подход | Когда использовать |
|--------|-------------------|
| **Classpath scanning** | Модули в одном приложении (монолит или многомодульный проект) |
| **Remote module scanning** | Модули в отдельных контейнерах (микросервисы) |
| **Manual registration** | Динамическая регистрация эндпоинтов во время работы |
