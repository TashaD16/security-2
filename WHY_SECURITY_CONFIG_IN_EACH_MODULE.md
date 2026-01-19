# Почему SecurityConfig в каждом модуле?

## Проблема

В проекте есть SecurityConfig в трех местах:
1. `gateway/SecurityConfig` - для gateway модуля
2. `moduleA/SecurityConfig` - для moduleA
3. `moduleB/SecurityConfig` - для moduleB

## Причина: Разные типы Spring Security

### Gateway модуль (WebFlux - Reactive)
```java
@EnableWebFluxSecurity          // ← Reactive Security
@EnableReactiveMethodSecurity    // ← Для reactive @PreAuthorize
```

### Бизнес-модули (Spring MVC - Servlet)
```java
@EnableWebSecurity              // ← Servlet Security
@EnableMethodSecurity           // ← Для servlet @PreAuthorize
```

**Это разные типы безопасности!** Они не могут использовать одну конфигурацию.

## Зачем нужен SecurityConfig в бизнес-модулях?

### Главная причина: `@EnableMethodSecurity(prePostEnabled = true)`

Без этой аннотации **@PreAuthorize не будет работать**!

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // ← БЕЗ ЭТОГО @PreAuthorize НЕ РАБОТАЕТ!
public class SecurityConfig {
    // ...
}
```

### Что делает `@EnableMethodSecurity`?

1. **Активирует AOP прокси** для обработки аннотаций безопасности
2. **Включает обработку** `@PreAuthorize` и `@PostAuthorize`
3. **Создает SecurityContext** для проверки authorities

### Без SecurityConfig в модуле:

```java
// ❌ НЕ РАБОТАЕТ - @PreAuthorize игнорируется
@GetMapping
@RequireReadDeclaration  // Spring Security не обработает эту аннотацию!
public ResponseEntity<List<DeclarationDto>> getAllDeclarations() { }
```

### С SecurityConfig в модуле:

```java
// ✅ РАБОТАЕТ - @PreAuthorize обрабатывается
@GetMapping
@RequireReadDeclaration  // Spring Security проверит права доступа
public ResponseEntity<List<DeclarationDto>> getAllDeclarations() { }
```

## Почему нельзя использовать одну конфигурацию?

### Проблема 1: Разные типы безопасности

- **Gateway**: WebFlux (reactive) - `SecurityWebFilterChain`
- **ModuleA/ModuleB**: Servlet (MVC) - `SecurityFilterChain`

Это **несовместимые** типы!

### Проблема 2: Независимые приложения

Каждый модуль - это **отдельное Spring Boot приложение**:
- Своя JVM
- Свой ApplicationContext
- Свои бины

Конфигурация из gateway **не видна** в moduleA и moduleB.

## Можно ли упростить?

### Вариант 1: Вынести в commons (НЕ РАБОТАЕТ)

```java
// ❌ НЕВОЗМОЖНО - разные типы Security
// Gateway нужен EnableWebFluxSecurity
// ModuleA нужен EnableWebSecurity
// Нельзя использовать одну конфигурацию!
```

### Вариант 2: Убрать SecurityConfig из бизнес-модулей (НЕ РАБОТАЕТ)

```java
// ❌ БЕЗ @EnableMethodSecurity @PreAuthorize НЕ РАБОТАЕТ!
// Аннотации будут просто игнорироваться
```

### Вариант 3: Текущий подход (РАБОТАЕТ ✅)

Каждый модуль имеет свою минимальную конфигурацию:
- Gateway: для reactive security
- ModuleA/ModuleB: для активации @PreAuthorize

## Что можно оптимизировать?

### Создать базовый класс в commons:

```java
// commons/src/main/java/.../BaseSecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public abstract class BaseSecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
        return http.build();
    }
}
```

### Использовать в модулях:

```java
// moduleA/SecurityConfig.java
@Configuration
public class SecurityConfig extends BaseSecurityConfig {
    // Наследует всю конфигурацию
}
```

**НО:** Это не сильно упростит код, так как конфигурация и так минимальная.

## Итог

SecurityConfig в каждом модуле нужен потому что:

1. ✅ **Активирует @PreAuthorize** - без `@EnableMethodSecurity` аннотации не работают
2. ✅ **Разные типы безопасности** - Gateway (WebFlux) vs ModuleA/B (Servlet)
3. ✅ **Независимые приложения** - каждый модуль работает отдельно
4. ✅ **Минимальная конфигурация** - только то, что нужно для работы аннотаций

**Вывод:** Это необходимо и правильно! Без SecurityConfig в каждом модуле @PreAuthorize не будет работать.
