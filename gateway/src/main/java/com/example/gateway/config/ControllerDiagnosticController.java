package com.example.gateway.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST контроллер для диагностики поиска контроллеров.
 * Позволяет проверить, какие контроллеры находятся в указанном пакете.
 */
@RestController
@RequestMapping("/diagnostic")
public class ControllerDiagnosticController {

    /**
     * Находит контроллеры в указанном пакете
     * GET /diagnostic/find?package=com.example.moduleA.controller
     */
    @GetMapping("/find")
    public Map<String, Object> findControllers(@RequestParam String packageName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<ControllerFinder.ControllerInfo> controllers = ControllerFinder.findControllers(packageName);
            
            result.put("success", true);
            result.put("package", packageName);
            result.put("count", controllers.size());
            result.put("controllers", controllers.stream()
                .map(info -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("className", info.className);
                    map.put("simpleName", info.simpleName);
                    map.put("packageName", info.packageName);
                    map.put("resourceLocation", info.resourceLocation);
                    return map;
                })
                .collect(Collectors.toList()));
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Находит все контроллеры в classpath
     * GET /diagnostic/find-all
     */
    @GetMapping("/find-all")
    public Map<String, Object> findAllControllers() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<ControllerFinder.ControllerInfo> controllers = ControllerFinder.findAllControllers();
            
            result.put("success", true);
            result.put("count", controllers.size());
            result.put("controllers", controllers.stream()
                .map(info -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("className", info.className);
                    map.put("simpleName", info.simpleName);
                    map.put("packageName", info.packageName);
                    map.put("resourceLocation", info.resourceLocation);
                    return map;
                })
                .collect(Collectors.toList()));
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
