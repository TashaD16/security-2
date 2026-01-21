package com.example.commons.security.controller;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Базовый контроллер, который должен быть добавлен в каждый модуль (микросервис).
 * Предоставляет endpoint для получения списка эндпоинтов с аннотациями безопасности.
 * 
 * Gateway будет вызывать этот endpoint для получения информации об эндпоинтах модуля.
 * 
 * ВАЖНО: В каждом модуле нужно создать свой контроллер, наследующий этот класс,
 * и реализовать метод scanControllers() для сканирования контроллеров модуля.
 */
@RestController
@RequestMapping("/api/module")
public class ModuleEndpointsController {

    /**
     * Возвращает список всех эндпоинтов модуля с их аннотациями безопасности.
     * GET /api/module/endpoints
     * 
     * Gateway будет вызывать этот endpoint для регистрации эндпоинтов.
     */
    @GetMapping("/endpoints")
    public ModuleEndpointsResponse getEndpoints() {
        ModuleEndpointsResponse response = new ModuleEndpointsResponse();
        response.moduleName = getModuleName();
        response.endpoints = scanControllers();
        return response;
    }

    /**
     * Сканирует контроллеры текущего модуля и возвращает список эндпоинтов.
     * Должен быть переопределен в каждом модуле для сканирования его контроллеров.
     */
    protected List<EndpointInfo> scanControllers() {
        // Базовая реализация возвращает пустой список
        // В каждом модуле нужно переопределить этот метод
        return new ArrayList<>();
    }

    /**
     * Получает имя модуля (можно переопределить в каждом модуле)
     */
    protected String getModuleName() {
        String packageName = this.getClass().getPackage().getName();
        if (packageName.contains("moduleA")) {
            return "moduleA";
        } else if (packageName.contains("moduleB")) {
            return "moduleB";
        }
        return "unknown";
    }

    /**
     * Ответ со списком эндпоинтов
     */
    public static class ModuleEndpointsResponse {
        public String moduleName;
        public List<EndpointInfo> endpoints = new ArrayList<>();
    }

    /**
     * Информация об эндпоинте
     */
    public static class EndpointInfo {
        public String httpMethod;
        public String path;
        public String annotationType;

        public EndpointInfo() {
        }

        public EndpointInfo(String httpMethod, String path, String annotationType) {
            this.httpMethod = httpMethod;
            this.path = path;
            this.annotationType = annotationType;
        }
    }
}
