package com.example.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST контроллер для регистрации эндпоинтов от удаленных модулей.
 * Каждый модуль (микросервис) может зарегистрировать свои эндпоинты через этот API.
 */
@RestController
@RequestMapping("/api/gateway/endpoints")
public class EndpointRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(EndpointRegistrationController.class);

    private final EndpointRegistrationService registrationService;

    public EndpointRegistrationController(EndpointRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Регистрация одного эндпоинта
     * POST /api/gateway/endpoints/register
     * 
     * Body:
     * {
     *   "moduleName": "moduleA",
     *   "httpMethod": "GET",
     *   "path": "/api/declarations",
     *   "annotationType": "RequireReadDeclaration"
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerEndpoint(@RequestBody Map<String, String> request) {
        try {
            String moduleName = request.get("moduleName");
            String httpMethod = request.get("httpMethod");
            String path = request.get("path");
            String annotationType = request.get("annotationType");

            if (moduleName == null || httpMethod == null || path == null || annotationType == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Missing required fields: moduleName, httpMethod, path, annotationType"
                ));
            }

            boolean registered = registrationService.registerEndpoint(moduleName, httpMethod, path, annotationType);
            
            return ResponseEntity.ok(Map.of(
                "success", registered,
                "moduleName", moduleName,
                "endpoint", httpMethod + ":" + path,
                "annotationType", annotationType
            ));
        } catch (Exception e) {
            log.error("Error registering endpoint", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Регистрация нескольких эндпоинтов
     * POST /api/gateway/endpoints/register-batch
     * 
     * Body:
     * {
     *   "moduleName": "moduleA",
     *   "endpoints": [
     *     {
     *       "httpMethod": "GET",
     *       "path": "/api/declarations",
     *       "annotationType": "RequireReadDeclaration"
     *     },
     *     {
     *       "httpMethod": "POST",
     *       "path": "/api/declarations",
     *       "annotationType": "RequireWriteDeclaration"
     *     }
     *   ]
     * }
     */
    @PostMapping("/register-batch")
    public ResponseEntity<Map<String, Object>> registerEndpoints(@RequestBody Map<String, Object> request) {
        try {
            String moduleName = (String) request.get("moduleName");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> endpointsData = (List<Map<String, String>>) request.get("endpoints");

            if (moduleName == null || endpointsData == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Missing required fields: moduleName, endpoints"
                ));
            }

            List<EndpointRegistrationService.EndpointInfo> endpoints = endpointsData.stream()
                .map(data -> new EndpointRegistrationService.EndpointInfo(
                    data.get("httpMethod"),
                    data.get("path"),
                    data.get("annotationType")
                ))
                .toList();

            int registered = registrationService.registerEndpoints(moduleName, endpoints);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "moduleName", moduleName,
                "registered", registered,
                "total", endpoints.size()
            ));
        } catch (Exception e) {
            log.error("Error registering endpoints", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
