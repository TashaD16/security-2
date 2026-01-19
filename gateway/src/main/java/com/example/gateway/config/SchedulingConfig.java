package com.example.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Конфигурация для включения поддержки @Scheduled аннотаций.
 * Необходима для работы AutoRescanService.
 * 
 * Если в вашем проекте уже есть @EnableScheduling, этот класс не нужен.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "gateway.auto-rescan.enabled", havingValue = "true", matchIfMissing = false)
public class SchedulingConfig {
}
