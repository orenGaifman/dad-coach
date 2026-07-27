package com.dadcoach.common;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs application startup information: active profile, server port, and database connection status.
 */
@Component
public class StartupLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(StartupLoggingListener.class);

    private final Environment environment;
    private final DataSource dataSource;

    public StartupLoggingListener(Environment environment, DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profiles = activeProfiles.length > 0
                ? String.join(", ", activeProfiles)
                : "default";

        String port = environment.getProperty("server.port", "8080");
        String dbUrl = environment.getProperty("spring.datasource.url", "unknown");

        String dbStatus = checkDatabaseConnection();

        log.info("Application started: profiles={}, port={}, database.url={}, database.status={}",
                profiles, port, dbUrl, dbStatus);
    }

    private String checkDatabaseConnection() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(5) ? "connected" : "unreachable";
        } catch (Exception e) {
            log.warn("Database connection check failed: {}", e.getMessage());
            return "unreachable";
        }
    }
}
