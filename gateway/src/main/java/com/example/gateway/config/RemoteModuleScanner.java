package com.example.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Сканер для получения информации об эндпоинтах от удаленных модулей (микросервисов).
 * Используется когда каждый модуль находится в отдельном контейнере.
 * 
 * Каждый модуль должен предоставлять endpoint для получения списка своих эндпоинтов.
 */
@Component
public class RemoteModuleScanner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RemoteModuleScanner.class);

    private final EndpointRegistrationService registrationService;
    private final WebClient webClient;

    @Value("${gateway.remote-modules.enabled:false}")
    private boolean enabled;

    @Value("${gateway.remote-modules.endpoints:/api/module/endpoints}")
    private String endpointsPath;

    @Value("${gateway.remote-modules.urls:}")
    private String moduleUrls; // Формат: moduleA:http://localhost:8081,moduleB:http://localhost:8082

    @Value("${gateway.remote-modules.scan-on-startup:true}")
    private boolean scanOnStartup;

    @Value("${gateway.remote-modules.retry-attempts:3}")
    private int retryAttempts;

    @Value("${gateway.remote-modules.timeout:5000}")
    private int timeoutMs;

    public RemoteModuleScanner(EndpointRegistrationService registrationService) {
        this.registrationService = registrationService;
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
    }

    @Override
    public void run(String... args) throws Exception {
        if (!enabled) {
            log.info("Remote module scanning is disabled");
            return;
        }

        if (!scanOnStartup) {
            log.info("Remote module scanning on startup is disabled");
            return;
        }

        log.info("Starting remote module scanning...");
        scanAllModules();
    }

    /**
     * Сканирует все настроенные модули
     */
    public void scanAllModules() {
        if (moduleUrls == null || moduleUrls.trim().isEmpty()) {
            log.warn("No remote modules configured. Set 'gateway.remote-modules.urls' property");
            return;
        }

        String[] modules = moduleUrls.split(",");
        log.info("Scanning {} remote modules", modules.length);

        for (String moduleConfig : modules) {
            String[] parts = moduleConfig.trim().split(":");
            if (parts.length < 2) {
                log.warn("Invalid module configuration: {}. Expected format: moduleName:url", moduleConfig);
                continue;
            }

            String moduleName = parts[0].trim();
            String baseUrl = String.join(":", Arrays.copyOfRange(parts, 1, parts.length));
            
            scanModule(moduleName, baseUrl);
        }
    }

    /**
     * Сканирует конкретный модуль
     */
    public void scanModule(String moduleName, String baseUrl) {
        log.info("Scanning module {} at {}", moduleName, baseUrl);
        
        String url = baseUrl + endpointsPath;
        
        try {
            ModuleEndpointsResponse response = webClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(ModuleEndpointsResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .retry(retryAttempts)
                .block();

            if (response != null && response.endpoints != null) {
                int registered = registrationService.registerEndpoints(moduleName, response.endpoints);
                log.info("Successfully registered {}/{} endpoints from module {}", 
                        registered, response.endpoints.size(), moduleName);
            } else {
                log.warn("No endpoints received from module {}", moduleName);
            }
        } catch (Exception e) {
            log.error("Error scanning module {} at {}: {}", moduleName, url, e.getMessage());
            log.debug("Exception details", e);
        }
    }

    /**
     * Ответ от модуля со списком эндпоинтов
     */
    public static class ModuleEndpointsResponse {
        public String moduleName;
        public List<EndpointRegistrationService.EndpointInfo> endpoints;

        public ModuleEndpointsResponse() {
        }
    }
}
