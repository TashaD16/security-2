# Объяснение работы @PreAuthorize в кастомных аннотациях

## Что такое @PreAuthorize?

`@PreAuthorize` - это **мета-аннотация Spring Security**, которая активирует проверку прав доступа **перед** выполнением метода.

## Как это работает в нашем проекте?

### 1. Структура кастомной аннотации

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('READ_DECLARATION') or hasAuthority('ADMIN')")
public @interface RequireReadDeclaration {
}
```

### 2. Что происходит при использовании

Когда вы используете `@RequireReadDeclaration` на методе:

```java
@GetMapping("/{declarationId}")
@RequireReadDeclaration
public ResponseEntity<DeclarationDto> getDeclaration(...) {
    // ...
}
```

**Spring Security выполняет следующее:**

1. **Видит аннотацию** `@RequireReadDeclaration` на методе
2. **Находит внутри** мета-аннотацию `@PreAuthorize`
3. **Выполняет выражение** `"hasAuthority('READ_DECLARATION') or hasAuthority('ADMIN')"`
4. **Проверяет authorities** текущего пользователя:
   - Есть ли `READ_DECLARATION`?
   - Или есть ли `ADMIN`?
5. **Если проверка пройдена** → метод выполняется
6. **Если проверка не пройдена** → возвращается `403 Forbidden`

### 3. Зачем нужна @PreAuthorize внутри кастомной аннотации?

#### Без @PreAuthorize (НЕ РАБОТАЕТ):
```java
// ❌ Это просто аннотация-маркер, Spring Security её не понимает
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireReadDeclaration {
}
```

#### С @PreAuthorize (РАБОТАЕТ):
```java
// ✅ Spring Security видит @PreAuthorize и выполняет проверку
@PreAuthorize("hasAuthority('READ_DECLARATION') or hasAuthority('ADMIN')")
public @interface RequireReadDeclaration {
}
```

### 4. Почему используется мета-аннотация?

**Преимущества:**

1. **Переиспользование** - одно правило определено один раз
2. **Читаемость** - `@RequireReadDeclaration` понятнее, чем длинная строка
3. **Типобезопасность** - ошибки видны на этапе компиляции
4. **DRY принцип** - не дублируем одно и то же правило в каждом методе

**Без кастомной аннотации (плохо):**
```java
@GetMapping
@PreAuthorize("hasAuthority('READ_DECLARATION') or hasAuthority('ADMIN')")
public ResponseEntity<List<DeclarationDto>> getAllDeclarations() { }

@GetMapping("/{id}")
@PreAuthorize("hasAuthority('READ_DECLARATION') or hasAuthority('ADMIN')")
public ResponseEntity<DeclarationDto> getDeclaration(...) { }
// Дублирование! ❌
```

**С кастомной аннотацией (хорошо):**
```java
@GetMapping
@RequireReadDeclaration
public ResponseEntity<List<DeclarationDto>> getAllDeclarations() { }

@GetMapping("/{id}")
@RequireReadDeclaration
public ResponseEntity<DeclarationDto> getDeclaration(...) { }
// Правило определено один раз! ✅
```

## Как включается обработка @PreAuthorize?

В конфигурации модуля должно быть:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // ← Это включает @PreAuthorize
public class SecurityConfig {
    // ...
}
```

`prePostEnabled = true` включает:
- `@PreAuthorize` - проверка **перед** выполнением метода
- `@PostAuthorize` - проверка **после** выполнения метода

## Выражения в @PreAuthorize

### Основные функции:

- `hasAuthority('AUTHORITY')` - проверяет наличие authority
- `hasRole('ROLE')` - проверяет наличие роли (автоматически добавляет префикс ROLE_)
- `hasAnyAuthority('AUTH1', 'AUTH2')` - проверяет наличие любого из authorities
- `hasAnyRole('ROLE1', 'ROLE2')` - проверяет наличие любой из ролей
- `isAuthenticated()` - проверяет, что пользователь аутентифицирован
- `permitAll()` - разрешает всем
- `denyAll()` - запрещает всем

### Логические операторы:

- `or` - логическое ИЛИ
- `and` - логическое И
- `!` - отрицание

### Примеры:

```java
// Только для администраторов
@PreAuthorize("hasAuthority('ADMIN')")

// Для администраторов ИЛИ пользователей с правом чтения
@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('READ_DECLARATION')")

// Для аутентифицированных пользователей
@PreAuthorize("isAuthenticated()")

// Для администраторов И пользователей с правом записи
@PreAuthorize("hasAuthority('ADMIN') and hasAuthority('WRITE_DECLARATION')")
```

## Итог

`@PreAuthorize` внутри кастомной аннотации - это **механизм активации проверки прав доступа** Spring Security. Без неё кастомная аннотация была бы просто маркером без функциональности.
