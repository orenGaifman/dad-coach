package com.dadcoach.api.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HealthControllerTest {

    private DataSource dataSource;
    private AiProviderHealthIndicator aiProviderHealthIndicator;
    private WhatsAppHealthIndicator whatsAppHealthIndicator;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        aiProviderHealthIndicator = mock(AiProviderHealthIndicator.class);
        whatsAppHealthIndicator = mock(WhatsAppHealthIndicator.class);
        controller = new HealthController(dataSource, aiProviderHealthIndicator, whatsAppHealthIndicator);
    }

    @Test
    void shouldReturnUpStatus_whenAllSubsystemsHealthy() throws SQLException {
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(2)).thenReturn(true);
        when(aiProviderHealthIndicator.checkHealth()).thenReturn("UP");
        when(aiProviderHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true));
        when(whatsAppHealthIndicator.checkHealth()).thenReturn("UP");
        when(whatsAppHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true));

        Map<String, Object> result = controller.detailedHealth();

        assertThat(result.get("status")).isEqualTo("UP");
        assertThat(result).containsKey("timestamp");
        assertThat(result).containsKey("subsystems");

        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) result.get("subsystems");
        assertThat(subsystems).containsKeys("database", "ai_provider", "whatsapp_api");

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) subsystems.get("database");
        assertThat(db.get("status")).isEqualTo("UP");
        assertThat(db.get("type")).isEqualTo("postgresql");
    }

    @Test
    void shouldReturnDownStatus_whenDatabaseIsDown() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        when(aiProviderHealthIndicator.checkHealth()).thenReturn("UP");
        when(aiProviderHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true));
        when(whatsAppHealthIndicator.checkHealth()).thenReturn("UP");
        when(whatsAppHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true));

        Map<String, Object> result = controller.detailedHealth();

        assertThat(result.get("status")).isEqualTo("DOWN");

        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) result.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) subsystems.get("database");
        assertThat(db.get("status")).isEqualTo("DOWN");
    }

    @Test
    void shouldReturnDegradedStatus_whenAiProviderIsDown() throws SQLException {
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(2)).thenReturn(true);
        when(aiProviderHealthIndicator.checkHealth()).thenReturn("DOWN");
        when(aiProviderHealthIndicator.getDetails()).thenReturn(Map.of("reachable", false, "error", "timeout"));
        when(whatsAppHealthIndicator.checkHealth()).thenReturn("UP");
        when(whatsAppHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true));

        Map<String, Object> result = controller.detailedHealth();

        assertThat(result.get("status")).isEqualTo("DEGRADED");
    }

    @Test
    void shouldReturnDegradedStatus_whenWhatsAppApiIsDown() throws SQLException {
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(2)).thenReturn(true);
        when(aiProviderHealthIndicator.checkHealth()).thenReturn("UP");
        when(aiProviderHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true));
        when(whatsAppHealthIndicator.checkHealth()).thenReturn("DOWN");
        when(whatsAppHealthIndicator.getDetails()).thenReturn(Map.of("reachable", false));

        Map<String, Object> result = controller.detailedHealth();

        assertThat(result.get("status")).isEqualTo("DEGRADED");
    }

    @Test
    void shouldIncludeAiProviderDetails() throws SQLException {
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(2)).thenReturn(true);
        when(aiProviderHealthIndicator.checkHealth()).thenReturn("DEGRADED");
        when(aiProviderHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true, "response_code", 503));
        when(whatsAppHealthIndicator.checkHealth()).thenReturn("UP");
        when(whatsAppHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true));

        Map<String, Object> result = controller.detailedHealth();

        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) result.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> aiProvider = (Map<String, Object>) subsystems.get("ai_provider");
        assertThat(aiProvider.get("status")).isEqualTo("DEGRADED");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) aiProvider.get("details");
        assertThat(details.get("response_code")).isEqualTo(503);
    }

    @Test
    void shouldIncludeWhatsAppDetails() throws SQLException {
        Connection conn = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.isValid(2)).thenReturn(true);
        when(aiProviderHealthIndicator.checkHealth()).thenReturn("UP");
        when(aiProviderHealthIndicator.getDetails()).thenReturn(Map.of("reachable", true));
        when(whatsAppHealthIndicator.checkHealth()).thenReturn("UNCONFIGURED");
        when(whatsAppHealthIndicator.getDetails()).thenReturn(Map.of("configured", false, "reason", "phone_number_id not set"));

        Map<String, Object> result = controller.detailedHealth();

        @SuppressWarnings("unchecked")
        Map<String, Object> subsystems = (Map<String, Object>) result.get("subsystems");
        @SuppressWarnings("unchecked")
        Map<String, Object> whatsApp = (Map<String, Object>) subsystems.get("whatsapp_api");
        assertThat(whatsApp.get("status")).isEqualTo("UNCONFIGURED");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) whatsApp.get("details");
        assertThat(details.get("configured")).isEqualTo(false);
    }
}
