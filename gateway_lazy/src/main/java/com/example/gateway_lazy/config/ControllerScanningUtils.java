package com.example.gateway_lazy.config;

import com.example.commons.security.annotation.*;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.springframework.security.authorization.AuthorizationDecision;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

/**
 * Утилитный класс для общих методов сканирования контроллеров.
 * Используется в gateway_lazy модуле для избежания дублирования кода.
 */
public class ControllerScanningUtils {

    /**
     * Находит HTTP метод из аннотаций метода контроллера
     */
    public static String findHttpMethod(Method method) {
        if (AnnotationUtils.findAnnotation(method, GetMapping.class) != null) return "GET";
        if (AnnotationUtils.findAnnotation(method, PostMapping.class) != null) return "POST";
        if (AnnotationUtils.findAnnotation(method, PutMapping.class) != null) return "PUT";
        if (AnnotationUtils.findAnnotation(method, DeleteMapping.class) != null) return "DELETE";
        if (AnnotationUtils.findAnnotation(method, PatchMapping.class) != null) return "PATCH";
        return null;
    }

    /**
     * Находит путь метода из аннотаций
     */
    public static String findMethodPath(Method method) {
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

    /**
     * Находит базовый путь контроллера из аннотации @RequestMapping класса
     */
    public static String findBasePath(Class<?> controllerClass) {
        RequestMapping classMapping = AnnotationUtils.findAnnotation(controllerClass, RequestMapping.class);
        return classMapping != null && classMapping.value().length > 0 
            ? classMapping.value()[0] 
            : "";
    }

    /**
     * Интерфейс для получения метода авторизации
     */
    @FunctionalInterface
    public interface AuthorizationMethodProvider {
        BiFunction<Mono<Authentication>, AuthorizationContext, Mono<AuthorizationDecision>> 
            getMethod(String annotationType);
    }

    /**
     * Находит метод авторизации на основе аннотации безопасности
     */
    public static BiFunction<Mono<Authentication>, AuthorizationContext, Mono<AuthorizationDecision>> 
            findAuthorizationMethod(Method method, AuthorizationMethodProvider provider) {
        
        if (AnnotationUtils.findAnnotation(method, RequireReadDeclaration.class) != null) {
            return provider.getMethod("RequireReadDeclaration");
        }
        if (AnnotationUtils.findAnnotation(method, RequireWriteDeclaration.class) != null) {
            return provider.getMethod("RequireWriteDeclaration");
        }
        if (AnnotationUtils.findAnnotation(method, RequireApproveDeclaration.class) != null) {
            return provider.getMethod("RequireApproveDeclaration");
        }
        if (AnnotationUtils.findAnnotation(method, RequireReadWare.class) != null) {
            return provider.getMethod("RequireReadWare");
        }
        if (AnnotationUtils.findAnnotation(method, RequireWriteWare.class) != null) {
            return provider.getMethod("RequireWriteWare");
        }
        if (AnnotationUtils.findAnnotation(method, RequireManageInventory.class) != null) {
            return provider.getMethod("RequireManageInventory");
        }
        return null;
    }
}
