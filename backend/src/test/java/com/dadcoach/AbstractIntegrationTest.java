package com.dadcoach;

import com.dadcoach.support.FakeServers;
import com.dadcoach.support.TestClock;
import com.dadcoach.support.TestData;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for every test with a Spring context: ONE shared static PostgreSQL 17 container for the whole JVM (no
 * {@code @Testcontainers}/{@code @Container}: they would stop it after the first class), ONE cached context, and
 * ONE {@link FakeServers} standing in for the platform and Meta. No {@code @MockBean} (playbook §50). The database
 * is created by Flyway from empty (the V23 baseline + V24+), as a fresh install is. Every test starts from empty
 * product tables and a pinned clock (Tuesday 2026-11-03 12:00 in Israel).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.TestBeans.class)
@TestPropertySource(properties = {
        "tool-api.api-key=" + AbstractIntegrationTest.TOOL_KEY,
        "workflow.platform.scheduled-response.enabled=true",
        "workflow.platform.scheduled-response.api-key=" + AbstractIntegrationTest.CALLBACK_KEY,
        "workflow.platform.scheduled-response.template-name=" + AbstractIntegrationTest.TEMPLATE,
        "dad-coach.security.admin-api-key=" + AbstractIntegrationTest.ADMIN_KEY,
        "dad-coach.whatsapp.webhook-secret=" + AbstractIntegrationTest.WEBHOOK_SECRET,
        "dad-coach.whatsapp.verify-token=test-verify-token",
        "dad-coach.whatsapp.phone-number-id=100000000000001",
        "dad-coach.whatsapp.access-token=test-access-token",
        "dad-coach.whatsapp.inbound-sync=true",
        "dad-coach.web.base-url=https://app.dadcoach.test",
        "workflow.platform.enabled=true",
        "workflow.platform.api-key=test-platform-key-0123456789",
        "workflow.platform.read-timeout-ms=5000",
        // background scans never start on their own; tests drive them
        "dadcoach.platform-person-deletion.initial-delay=PT24H",
        "dadcoach.platform-person-deletion.send-after-commit=false",
        "dadcoach.scheduler.weekly-goal-completion-cron=-",
        "spring.datasource.hikari.maximum-pool-size=5",
        // the dashboard (com.dadcoach.auth / web / ops)
        "dadcoach.dashboard.ops-api-key=" + AbstractIntegrationTest.OPS_KEY,
        "dadcoach.dashboard.cookie-secure=false",
        "dadcoach.dashboard.whatsapp-public-number=+19995550100",
        // voice notes (D-029) are on in tests: ElevenLabs is FakeServers too
        "dad-coach.voice-notes.elevenlabs-api-key=" + AbstractIntegrationTest.ELEVENLABS_KEY,
        "dad-coach.voice-notes.timeout-ms=5000"
})
public abstract class AbstractIntegrationTest {

    public static final String TOOL_KEY = "test-only-tool-key-0123456789abcdef";
    public static final String CALLBACK_KEY = "test-only-callback-key-0123456789abc";
    public static final String ADMIN_KEY = "test-only-admin-key-0123456789abcdef";
    public static final String WEBHOOK_SECRET = "test-only-webhook-secret";
    public static final String TEMPLATE = "dad_coach_message_he";
    public static final String OPS_KEY = "test-ops-key-0123456789abcdef";
    public static final String ELEVENLABS_KEY = "test-elevenlabs-key";
    public static final Instant TUESDAY_NOON_IL = Instant.parse("2026-11-03T10:00:00Z");

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("dadcoach").withUsername("dadcoach").withPassword("dadcoach");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("workflow.platform.base-url", FakeServers.INSTANCE::baseUrl);
        registry.add("dad-coach.whatsapp.api-base-url", FakeServers.INSTANCE::baseUrl);
        registry.add("dad-coach.voice-notes.elevenlabs-base-url", FakeServers.INSTANCE::baseUrl);
    }

    public static PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TestClock clock;
    @Autowired protected TestData data;
    protected final FakeServers fake = FakeServers.INSTANCE;

    @BeforeEach
    void cleanSlate() {
        jdbc.execute("TRUNCATE quality_time, weekly_goal, goal, message_log, scheduled_response_delivery, child, "
                + "communication_endpoints, platform_person_deletion, tool_idempotency, template_messages, site_signup, "
                + "login_link, dashboard_session, father_deactivation, training_progress, staff_user, father, system_setting "
                + "RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO template_messages (template_name, language, category, body, status, max_variables) "
                + "VALUES (?, 'he', 'UTILITY', '{{1}}', 'APPROVED', 1)", TEMPLATE);
        clock.set(TUESDAY_NOON_IL);
        fake.reset();
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }
}
