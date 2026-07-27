package com.dadcoach.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's @Async support for non-blocking operations
 * such as AI telemetry writes.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
