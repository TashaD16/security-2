# Как убедиться, что модули с контроллерами находятся в classpath

## Что такое classpath?

**Classpath** - это путь, по которому Java ищет классы и ресурсы. В Spring Boot приложениях classpath включает:
- Скомпилированные классы из ваших модулей
- Зависимости из Maven/Gradle
- Ресурсы из `src/main/resources`

## Как модули попадают в classpath?

### Сценарий 1: Многомодульный Maven проект

Если у вас многомодульный проект, модули попадают в classpath через зависимости.

#### Шаг 1: Убедитесь, что модули собраны

```bash
# Соберите весь проект из корня
mvn clean install
```

Это создаст JAR файлы для каждого модуля и установит их в локальный Maven репозиторий.

#### Шаг 2: Добавьте зависимость на модуль с контроллерами

В `pom.xml` модуля, который запускается (обычно это модуль с `@SpringBootApplication`), добавьте зависимость на модуль с контроллерами:

```xml
<dependencies>
    <!-- Модуль с контроллерами -->
    <dependency>
        <groupId>com.yourproject</groupId>
        <artifactId>module-with-controllers</artifactId>
        <version>${project.version}</version>
    </dependency>
    
    <!-- Модуль gateway (для авторизации) -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>gateway</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

#### Шаг 3: Проверьте, что модуль в classpath

**Способ 1: Проверка через Maven**

```bash
# Показывает все зависимости, включая транзитивные
mvn dependency:tree

# Ищите ваш модуль в выводе
# Должно быть что-то вроде:
# com.yourproject:module-with-controllers:jar:1.0.0:compile
```

**Способ 2: Проверка через код**

Создайте временный класс для проверки:

```java
@RestController
public class ClasspathChecker {
    
    @GetMapping("/check-classpath")
    public Map<String, Object> checkClasspath() {
        Map<String, Object> result = new HashMap<>();
        
        // Проверяем, доступен ли класс контроллера
        try {
            Class<?> controllerClass = Class.forName("com.yourproject.module.controller.YourController");
            result.put("controllerFound", true);
            result.put("controllerClass", controllerClass.getName());
        } catch (ClassNotFoundException e) {
            result.put("controllerFound", false);
            result.put("error", e.getMessage());
        }
        
        // Показываем classpath
        String classpath = System.getProperty("java.class.path");
        result.put("classpath", classpath);
        
        return result;
    }
}
```

**Способ 3: Проверка через логи при запуске**

При запуске приложения в логах должно быть видно:

```
INFO  - Scanning controllers in packages: com.yourproject.module.controller
INFO  - Scanning pattern: classpath*:com/yourproject/module/controller/**/*.class
INFO  - Total resources scanned: X, Controllers found: Y
```

Если `Total resources scanned: 0`, значит модуль не в classpath.

---

### Сценарий 2: Модули в одном проекте (без отдельного JAR)

Если все модули находятся в одном проекте и собираются вместе:

#### Шаг 1: Убедитесь, что модули в родительском pom.xml

```xml
<modules>
    <module>module-with-controllers</module>
    <module>gateway</module>
    <module>main-module</module>  <!-- Модуль, который запускается -->
</modules>
```

#### Шаг 2: Добавьте зависимость

В `pom.xml` модуля, который запускается:

```xml
<dependencies>
    <dependency>
        <groupId>com.yourproject</groupId>
        <artifactId>module-with-controllers</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

#### Шаг 3: Соберите проект

```bash
mvn clean install
```

При запуске Spring Boot автоматически включит все классы из зависимостей в classpath.

---

### Сценарий 3: Использование библиотеки (JAR файл)

Если модуль с контроллерами упакован в JAR и используется как библиотека:

#### Шаг 1: Установите JAR в локальный репозиторий

```bash
cd module-with-controllers
mvn clean install
```

#### Шаг 2: Добавьте зависимость

```xml
<dependency>
    <groupId>com.yourproject</groupId>
    <artifactId>module-with-controllers</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Шаг 3: Проверьте, что JAR в classpath

```bash
# Показывает все JAR файлы в classpath
mvn dependency:build-classpath

# Или проверьте в target/classes или target/dependency
```

---

## Диагностика проблем

### Проблема 1: Модуль не найден при сканировании

**Симптомы:**
```
INFO  - Total resources scanned: 0, Controllers found: 0
WARN  - No resources found for scanning patterns
```

**Решения:**

1. **Проверьте, что модуль собран:**
   ```bash
   mvn clean install
   ```

2. **Проверьте зависимость:**
   ```bash
   mvn dependency:tree | grep "module-with-controllers"
   ```
   
   Если модуль не найден, добавьте зависимость в `pom.xml`.

3. **Проверьте правильность пакетов:**
   Убедитесь, что в `application.properties` указаны правильные пакеты:
   ```properties
   endpoint-scanner.scan-packages=com.yourproject.module.controller
   ```

4. **Проверьте, что контроллеры имеют аннотацию:**
   ```java
   @RestController  // ← Должна быть эта аннотация
   public class YourController {
       // ...
   }
   ```

### Проблема 2: Модуль в classpath, но контроллеры не сканируются

**Симптомы:**
```
INFO  - Total resources scanned: 5, Controllers found: 0
```

**Решения:**

1. **Проверьте аннотацию @RestController:**
   ```java
   @RestController  // ← Должна быть именно @RestController, не @Controller
   public class YourController {
   }
   ```

2. **Проверьте пакеты:**
   Убедитесь, что пакет контроллера точно совпадает с указанным в `endpoint-scanner.scan-packages`.

3. **Проверьте структуру пакетов:**
   Если контроллер в `com.yourproject.module.controller.subpackage`, добавьте:
   ```properties
   endpoint-scanner.scan-packages=com.yourproject.module.controller,com.yourproject.module.controller.subpackage
   ```

### Проблема 3: Модуль не компилируется

**Симптомы:**
```
[ERROR] Failed to compile
```

**Решения:**

1. **Проверьте зависимости модуля:**
   ```bash
   cd module-with-controllers
   mvn clean compile
   ```

2. **Проверьте версию Java:**
   Убедитесь, что все модули используют одну версию Java (17 в этом проекте).

---

## Практические примеры

### Пример 1: Типичная структура многомодульного проекта

```
your-project/
├── pom.xml                    (родительский)
├── module-api/                 (контроллеры здесь)
│   ├── pom.xml
│   └── src/main/java/com/yourproject/api/controller/
│       └── UserController.java
├── module-service/             (бизнес-логика)
│   └── pom.xml
└── module-main/                (запускаемый модуль)
    ├── pom.xml
    └── src/main/java/com/yourproject/MainApplication.java
```

**В `module-main/pom.xml`:**
```xml
<dependencies>
    <dependency>
        <groupId>com.yourproject</groupId>
        <artifactId>module-api</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

**В `application.properties` (в module-main):**
```properties
endpoint-scanner.scan-packages=com.yourproject.api.controller
```

### Пример 2: Использование внешней библиотеки

Если контроллеры находятся во внешней библиотеке (например, `common-api.jar`):

**В `pom.xml`:**
```xml
<dependency>
    <groupId>com.external</groupId>
    <artifactId>common-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

**В `application.properties`:**
```properties
endpoint-scanner.scan-packages=com.external.api.controller
```

---

## Быстрая проверка

### Чек-лист для проверки classpath:

- [ ] Модуль с контроллерами собран (`mvn clean install`)
- [ ] Модуль добавлен как зависимость в `pom.xml` запускаемого модуля
- [ ] Зависимость видна в `mvn dependency:tree`
- [ ] Пакеты контроллеров указаны в `endpoint-scanner.scan-packages`
- [ ] Контроллеры имеют аннотацию `@RestController`
- [ ] При запуске в логах видно: `Total resources scanned: > 0`

### Команды для проверки:

```bash
# 1. Собрать проект
mvn clean install

# 2. Проверить зависимости
mvn dependency:tree

# 3. Проверить classpath
mvn dependency:build-classpath

# 4. Запустить приложение и проверить логи
mvn spring-boot:run
```

---

## Дополнительная информация

### Как Spring Boot находит классы в classpath?

Spring Boot использует `PathMatchingResourcePatternResolver` для поиска классов:

1. Ищет по паттерну: `classpath*:com/yourproject/controller/**/*.class`
2. Сканирует все JAR файлы и директории в classpath
3. Проверяет аннотации через `MetadataReader`
4. Загружает классы через `Class.forName()`

### Важные моменты:

- **`classpath*:`** - ищет во всех JAR файлах и директориях
- **`classpath:`** - ищет только в первом найденном месте
- Контроллеры должны быть скомпилированы (`.class` файлы)
- Аннотация `@RestController` должна быть на уровне класса

---

## Если ничего не помогает

1. **Включите DEBUG логирование:**
   ```properties
   logging.level.com.example.gateway=DEBUG
   logging.level.org.springframework.core.io=DEBUG
   ```

2. **Проверьте структуру JAR файла:**
   ```bash
   jar -tf module-with-controllers/target/module-with-controllers-1.0.0.jar | grep controller
   ```

3. **Проверьте, что классы действительно скомпилированы:**
   ```bash
   ls -la module-with-controllers/target/classes/com/yourproject/controller/
   ```

4. **Попробуйте явно указать все подпакеты:**
   ```properties
   endpoint-scanner.scan-packages=com.yourproject.module1.controller,com.yourproject.module1.controller.v1,com.yourproject.module1.controller.v2
   ```
