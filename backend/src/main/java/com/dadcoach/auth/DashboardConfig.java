package com.dadcoach.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** Loads the dashboard's own property file (lowest precedence: environment and application.yml win). */
@Configuration
@PropertySource("classpath:dashboard.properties")
@EnableConfigurationProperties(DashboardProperties.class)
public class DashboardConfig {
}
