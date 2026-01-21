# Руководство по использованию ControllerFinder

## Описание

`ControllerFinder` - это простой утилитный класс для поиска и диагностики контроллеров в указанном модуле/пакете. Используется для решения проблем с поиском контроллеров.

## Способы использования

### 1. Использование из кода (Java)

```java
import com.example.gateway.config.ControllerFinder;

// Поиск контроллеров в конкретном пакете
List<ControllerFinder.ControllerInfo> controllers = 
    ControllerFinder.findControllers("com.example.moduleA.controller");

// Поиск всех контроллеров в classpath
List<ControllerFinder.ControllerInfo> allControllers = 
    ControllerFinder.findAllControllers();

// Вывод результатов
for (ControllerFinder.ControllerInfo info : controllers) {
    System.out.println("Найден контроллер: " + info.className);
    System.out.println("Расположение: " + info.resourceLocation);
}
```

### 2. Запуск как отдельное приложение

```bash
# Компиляция
mvn clean compile

# Запуск с указанием пакета
java -cp "target/classes:target/dependency/*" \
    com.example.gateway.config.ControllerFinder \
    com.example.moduleA.controller

# Поиск всех контроллеров
java -cp "target/classes:target/dependency/*" \
    com.example.gateway.config.ControllerFinder \
    --all
```

### 3. Использование через REST API (если приложение запущено)

Если приложение запущено, можно использовать диагностический контроллер:

```bash
# Поиск контроллеров в пакете
curl "http://localhost:8080/diagnostic/find?package=com.example.moduleA.controller"

# Поиск всех контроллеров
curl "http://localhost:8080/diagnostic/find-all"
```

## Пример вывода

```
========================================
Поиск контроллеров в пакете: com.example.moduleA.controller
Паттерн поиска: classpath*:com/example/moduleA/controller/**/*.class
========================================
Найдено ресурсов: 5
Проверка класса: com.example.moduleA.controller.DeclarationController
✓ НАЙДЕН КОНТРОЛЛЕР: com.example.moduleA.controller.DeclarationController
========================================
ИТОГО найдено контроллеров: 1
========================================

Найденные контроллеры:
1. com.example.moduleA.controller.DeclarationController
   Расположение: file [D:\project\target\classes\com\example\moduleA\controller\DeclarationController.class]
```

## Что проверяет ControllerFinder

1. **Правильность имени пакета** - проверяет, существует ли указанный пакет
2. **Наличие аннотации @RestController** - находит только классы с этой аннотацией
3. **Доступность в classpath** - проверяет, что модуль находится в classpath
4. **Компиляция** - проверяет, что классы скомпилированы

## Решение проблем

### Проблема: "КОНТРОЛЛЕРЫ НЕ НАЙДЕНЫ!"

**Возможные причины:**

1. **Неправильное имя пакета**
   - Проверьте точное имя пакета, где находятся контроллеры
   - Убедитесь, что нет опечаток

2. **Модуль не в classpath**
   - Проверьте зависимости в `pom.xml`
   - Выполните `mvn clean compile` из корня проекта
   - Проверьте, что модуль сконфигурирован как зависимость

3. **Отсутствует аннотация @RestController**
   - Убедитесь, что классы имеют аннотацию `@RestController`
   - Проверьте импорты: `import org.springframework.web.bind.annotation.RestController;`

4. **Классы не скомпилированы**
   - Выполните `mvn clean compile`
   - Проверьте, что в `target/classes` есть скомпилированные классы

### Диагностика

1. **Используйте `findAllControllers()`** для проверки, есть ли вообще контроллеры в classpath:
   ```java
   ControllerFinder.findAllControllers();
   ```

2. **Проверьте classpath** через Maven:
   ```bash
   mvn dependency:tree
   ```

3. **Проверьте структуру проекта** - убедитесь, что контроллеры находятся в правильных пакетах

## Интеграция с ControllerScanner

`ControllerScanner` автоматически использует `ControllerFinder` для поиска контроллеров. Если контроллеры не найдены, в логах будет предложено использовать `ControllerFinder` для диагностики.

## Структура ControllerInfo

```java
public static class ControllerInfo {
    public String className;        // Полное имя класса
    public String simpleName;       // Простое имя класса
    public String packageName;      // Имя пакета
    public String resourceLocation;  // Расположение файла .class
}
```
