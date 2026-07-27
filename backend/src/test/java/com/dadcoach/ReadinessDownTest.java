package com.dadcoach;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:59999/nonexistent",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "dad-coach.whatsapp.api-base-url=https://graph.facebook.com",
        "dad-coach.whatsapp.api-version=v25.0",
        "dad-coach.whatsapp.phone-number-id=test",
        "dad-coach.whatsapp.access-token=test",
        "dad-coach.whatsapp.verify-token=test"
})
class ReadinessDownTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void readinessReturnsDownWhenDatabaseUnavailable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health/readiness", String.class);
        assertThat(response.getBody()).contains("\"status\":\"DOWN\"");
    }
}
