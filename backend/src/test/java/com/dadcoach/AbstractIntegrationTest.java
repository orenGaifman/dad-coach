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
 *
 * <p>D-053 (Unified Workflow 6.4): the platform's Send API is the only sender - after every test, fake Meta must have
 * received no send at all ({@link #metaReceivedNoSend}); what a father receives is asserted on the {@code /send}
 * requests ({@link FakeServers#sendRequests}).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.TestBeans.class)
@TestPropertySource(properties = {
        "tool-api.api-key=" + AbstractIntegrationTest.TOOL_KEY,
        "workflow.platform.scheduled-response.enabled=true",
        "workflow.platform.scheduled-response.api-key=" + AbstractIntegrationTest.CALLBACK_KEY,
        "dad-coach.security.admin-api-key=" + AbstractIntegrationTest.ADMIN_KEY,
        "dad-coach.whatsapp.webhook-secret=" + AbstractIntegrationTest.WEBHOOK_SECRET,
        "dad-coach.whatsapp.verify-token=test-verify-token",
        "dad-coach.whatsapp.phone-number-id=100000000000001",
        "dad-coach.whatsapp.access-token=test-access-token",
        "dad-coach.whatsapp.waba-id=200000000000002",
        "dad-coach.whatsapp.inbound-sync=true",
        "dad-coach.web.base-url=https://app.dadcoach.test",
        "workflow.platform.enabled=true",
        "workflow.platform.api-key=test-platform-key-0123456789",
        "workflow.platform.read-timeout-ms=5000",
        // background scans never start on their own; tests drive them
        "dadcoach.platform-person-deletion.send-after-commit=false",
        "dadcoach.scheduler.weekly-goal-completion-cron=-",
        "dad-coach.messaging.reconcile-initial-delay=PT24H",
        "spring.datasource.hikari.maximum-pool-size=5",
        // 5.4 v3: the platform jobs' lock pool, small - every cached context has one
        "app.platform-jobs.lock-pool-size=2",
        "app.platform-jobs.watchdog-initial-delay=PT24H",
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
        registry.add("google.calendar.api-base-url", () -> FakeServers.INSTANCE.baseUrl() + "/google/calendar/v3");
        registry.add("google.calendar.token-url", () -> FakeServers.INSTANCE.baseUrl() + "/google/token");
    }

    public static PostgreSQLContainer<?> postgres() {
        return POSTGRES;
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TestClock clock;
    @Autowired protected TestData data;
    protected final FakeServers fake = FakeServers.INSTANCE;
    @Autowired private com.dadcoach.integration.platform.WorkflowPlatformProperties platformProperties;
    @Autowired private com.dadcoach.integration.platform.messaging.MessagingSettings messaging;
    @Autowired private com.dadcoach.integration.platform.messaging.PlatformTemplateStatus templateStatus;

    @BeforeEach
    void cleanSlate() {
        jdbc.execute("TRUNCATE quality_time, weekly_goal, goal, message_log, scheduled_response_delivery, child, "
                + "communication_endpoints, platform_person_deletion, tool_idempotency, template_messages, site_signup, "
                + "login_link, dashboard_session, father_deactivation, training_progress, staff_user, father, system_setting "
                + "RESTART IDENTITY CASCADE");
        clock.set(TUESDAY_NOON_IL);
        fake.reset();
        platformProperties.setDeliveryReports(false); // D-039: the switch is off unless a test turns it on
        templateStatus.forget(); // D-053: the platform's template view is cached for 5 minutes
        messaging.setReplyReadTimeoutMs(5000);
        messaging.setNotCreatedRepostWait(java.time.Duration.ofMillis(20)); // D-053: ≤ 2 s in production
        messaging.setDeliveryStatusEnabled(false);
        messaging.setReconcileAfter(java.time.Duration.ofMinutes(5));
        messaging.setReadTimeoutMs(12000);
        messaging.setLookupReadTimeoutMs(5000); // D-047
        messaging.setReconcilePassBudget(java.time.Duration.ofSeconds(15));
    }

    /** D-053: Dad Coach never sends to Meta - every message goes through the platform's Send API. */
    @org.junit.jupiter.api.AfterEach
    void metaReceivedNoSend() {
        org.assertj.core.api.Assertions.assertThat(fake.metaSends()).as("D-053: a send reached Meta directly").isEmpty();
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
