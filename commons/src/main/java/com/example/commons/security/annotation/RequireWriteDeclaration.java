package com.example.commons.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Кастомная аннотация для проверки права записи деклараций
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('WRITE_DECLARATION') or hasAuthority('ADMIN')")
public @interface RequireWriteDeclaration {
}
