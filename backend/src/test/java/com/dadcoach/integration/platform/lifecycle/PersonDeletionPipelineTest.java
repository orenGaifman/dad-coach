package com.dadcoach.integration.platform.lifecycle;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.domain.father.FatherDataPurger;
import com.dadcoach.integration.platform.PlatformWorkflowConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The father deletion pipeline on a real Postgres (the outbox migration V22 as shipped, plus the father tables it
 * purges) against a stand-in for the platform's person lifecycle API: register INACTIVE under the ref, delete,
 * confirm; retried with backoff through a platform outage; the local purge after a self-service deletion; and a
 * deleted father's number kept away from the platform until it confirms.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonDeletionPipelineTest {

    private static final UUID TENANT = UUID.fromString("20082bcd-a7bf-57a8-a382-4bad32144b2f");

    private PostgreSQLContainer<?> postgres;
    private WireMockServer platform;
    private JdbcTemplate jdbc;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2030-01-01T10:00:00Z"));
    private PlatformPersonDeletions deletions;

    @BeforeAll
    void start() throws Exception {
        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE father (id BIGSERIAL PRIMARY KEY, phone VARCHAR(32) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL)");
        jdbc.execute("CREATE TABLE child (id BIGSERIAL PRIMARY KEY, father_id BIGINT NOT NULL REFERENCES father(id), name TEXT)");
        jdbc.execute("CREATE TABLE families (id BIGSERIAL PRIMARY KEY, father_id UUID NOT NULL UNIQUE)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V22__platform_person_deletion.sql"), StandardCharsets.UTF_8));
        platform = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        platform.start();
    }

    @AfterAll
    void stop() {
        platform.stop();
        postgres.stop();
    }

    @BeforeEach
    void setUp() {
        platform.resetAll();
        jdbc.update("DELETE FROM platform_person_deletion");
        PlatformWorkflowConfig config = new PlatformWorkflowConfig();
        config.setEnabled(true);
        config.setBaseUrl("http://localhost:" + platform.port());
        config.setApiKey("dad-key");
        deletions = deletions(config, true);
    }

    private PlatformPersonDeletions deletions(PlatformWorkflowConfig config, boolean enabled) {
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        return new PlatformPersonDeletions(jdbc, new PlatformTenancyClient(config), new FatherDataPurger(jdbc), enabled, clock, r -> { });
    }

    @Test
    @DisplayName("admin delete: the person is registered INACTIVE under the father's ref, then deleted; the number is forgotten")
    void adminDeletionRegistersThenDeletes() {
        long father = father("+972501110001", "ACTIVE");
        String ref = PersonRefs.of(father);
        stubPlatform(ref, 200, "DELETED");

        deletions.request(father, "+972501110001", false);
        assertThat(deletions.isPending("+972501110001")).as("kept away from the platform until it confirms").isTrue();
        assertThat(deletions.sendDue()).isEqualTo(1);

        platform.verify(putRequestedFor(urlEqualTo("/api/v1/tenancy/people"))
                .withHeader("X-API-Key", equalTo("dad-key"))
                .withRequestBody(equalToJson("{\"tenantId\":\"" + TENANT + "\",\"dryRun\":false,\"people\":[{\"personRef\":\"" + ref
                        + "\",\"externalUserId\":\"whatsapp:+972501110001\",\"channel\":\"whatsapp\",\"status\":\"INACTIVE\"}]}", true, true)));
        platform.verify(deleteRequestedFor(urlEqualTo("/api/v1/tenancy/tenants/" + TENANT + "/people/" + ref)));
        Map<String, Object> row = row(father);
        assertThat(row.get("outcome")).isEqualTo("DELETED");
        assertThat(row.get("external_user_id")).as("no phone kept once done").isNull();
        assertThat(deletions.isPending("+972501110001")).isFalse();
        assertThat(count("father", father)).as("an admin delete purges at once, not here").isEqualTo(1);
    }

    @Test
    @DisplayName("self-service: once the platform confirms, the father's own data is purged; other fathers keep theirs")
    void selfServiceDeletionPurgesAfterThePlatformConfirms() {
        long father = father("+972501110002", "DELETED");
        long other = father("+972501110003", "ACTIVE");
        stubPlatform(PersonRefs.of(father), 200, "DELETED");

        deletions.request(father, "+972501110002", true);
        deletions.sendDue();

        assertThat(count("father", father)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM child WHERE father_id = ?", Integer.class, father)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM families WHERE father_id = ?", Integer.class, new UUID(0, father))).isZero();
        assertThat(count("father", other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM child WHERE father_id = ?", Integer.class, other)).isEqualTo(1);
        assertThat(row(father).get("outcome")).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("a platform outage is retried with backoff until it succeeds; nothing is purged before the platform confirms")
    void outageIsRetriedUntilDone() {
        long father = father("+972501110004", "DELETED");
        String ref = PersonRefs.of(father);
        platform.stubFor(put(urlEqualTo("/api/v1/tenancy/people")).inScenario("outage").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503).withHeader("Content-Type", "application/json").withBody("{\"code\":\"UNAVAILABLE\"}"))
                .willSetStateTo("up"));
        platform.stubFor(put(urlEqualTo("/api/v1/tenancy/people")).inScenario("outage").whenScenarioStateIs("up")
                .willReturn(json(peopleResult(""))));
        platform.stubFor(put(urlEqualTo("/api/v1/tenancy/people")).inScenario("outage").whenScenarioStateIs("idle")
                .willReturn(json(peopleResult(""))));
        platform.stubFor(delete(urlEqualTo("/api/v1/tenancy/tenants/" + TENANT + "/people/" + ref)).inScenario("outage")
                .whenScenarioStateIs("up").willReturn(aResponse().withStatus(409).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"person.busy\"}")).willSetStateTo("idle"));
        platform.stubFor(delete(urlEqualTo("/api/v1/tenancy/tenants/" + TENANT + "/people/" + ref)).inScenario("outage")
                .whenScenarioStateIs("idle").willReturn(json(deletion(ref, "DELETED"))));

        deletions.request(father, "+972501110004", true);
        assertThat(deletions.sendDue()).isZero();
        assertThat(row(father).get("last_error")).isEqualTo("503 UNAVAILABLE");
        assertThat(next(father)).isEqualTo(now.get().plus(Duration.ofMinutes(1)));
        assertThat(count("father", father)).as("nothing purged before the platform confirms").isEqualTo(1);
        assertThat(deletions.sendDue()).as("not due before its backoff").isZero();

        tick(Duration.ofMinutes(1));
        assertThat(deletions.sendDue()).isZero();
        assertThat(row(father).get("last_error")).isEqualTo("409 person.busy");
        assertThat(next(father)).isEqualTo(now.get().plus(Duration.ofMinutes(2)));

        tick(Duration.ofMinutes(2));
        assertThat(deletions.sendDue()).isEqualTo(1);
        assertThat(row(father).get("attempts")).isEqualTo(3);
        assertThat(count("father", father)).isZero();
    }

    @Test
    @DisplayName("a number the platform has under another person is never guessed: retried and logged, nothing deleted")
    void registrationConflictIsNotGuessed() {
        long father = father("+972501110005", "DELETED");
        platform.stubFor(put(urlEqualTo("/api/v1/tenancy/people"))
                .willReturn(json(peopleResult("{\"personRef\":\"x\",\"idTail\":\"0005\",\"reason\":\"identity already belongs to another product person\"}"))));

        deletions.request(father, "+972501110005", true);
        deletions.sendDue();

        platform.verify(0, deleteRequestedFor(urlEqualTo("/api/v1/tenancy/tenants/" + TENANT + "/people/" + PersonRefs.of(father))));
        assertThat(row(father).get("completed_at")).isNull();
        assertThat((String) row(father).get("last_error")).contains("another product person");
        assertThat(count("father", father)).isEqualTo(1);
    }

    @Test
    @DisplayName("backoff doubles to at most 6 hours and the request is never dropped; switched off it waits; one row per father")
    void backoffSwitchAndIdempotence() {
        assertThat(PlatformPersonDeletions.backoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(PlatformPersonDeletions.backoff(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(PlatformPersonDeletions.backoff(30)).isEqualTo(Duration.ofHours(6));

        long father = father("+972501110006", "DELETED");
        PlatformWorkflowConfig off = new PlatformWorkflowConfig();
        PlatformPersonDeletions switchedOff = deletions(off, false);
        switchedOff.request(father, "+972501110006", true);
        switchedOff.request(father, "+972501110006", true);
        assertThat(switchedOff.sendDue()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_person_deletion WHERE father_id = ?", Integer.class, father)).isEqualTo(1);
        platform.verify(0, putRequestedFor(urlEqualTo("/api/v1/tenancy/people")));
    }

    // ------------------------------------------------------------------------------------------ fixtures

    private long father(String phone, String status) {
        Long id = jdbc.queryForObject("INSERT INTO father (phone, status) VALUES (?, ?) RETURNING id", Long.class, phone, status);
        jdbc.update("INSERT INTO child (father_id, name) VALUES (?, 'Noa')", id);
        jdbc.update("INSERT INTO families (father_id) VALUES (?)", new UUID(0, id));
        return id;
    }

    private void stubPlatform(String ref, int status, String outcome) {
        platform.stubFor(put(urlEqualTo("/api/v1/tenancy/people")).willReturn(json(peopleResult(""))));
        platform.stubFor(delete(urlEqualTo("/api/v1/tenancy/tenants/" + TENANT + "/people/" + ref))
                .willReturn(json(deletion(ref, outcome)).withStatus(status)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static String peopleResult(String conflict) {
        return "{\"tenantId\":\"" + TENANT + "\",\"dryRun\":false,\"created\":0,\"updated\":1,\"unchanged\":0,"
                + "\"linkedWorkerInstances\":0,\"linkedWorkflowInstances\":0,\"conflicts\":[" + conflict + "]}";
    }

    private static String deletion(String ref, String outcome) {
        return "{\"tenantId\":\"" + TENANT + "\",\"personRef\":\"" + ref + "\",\"outcome\":\"" + outcome + "\",\"dryRun\":false,"
                + "\"workflowInstances\":1,\"workerInstances\":1,\"messages\":6,\"agentExecutions\":3,\"scheduledTriggers\":1,"
                + "\"conversationReviews\":0,\"profileDeleted\":true}";
    }

    private Map<String, Object> row(long father) {
        return jdbc.queryForMap("SELECT * FROM platform_person_deletion WHERE father_id = ?", father);
    }

    private Instant next(long father) {
        return ((java.sql.Timestamp) row(father).get("next_attempt_at")).toInstant();
    }

    private int count(String table, long id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private void tick(Duration duration) {
        now.set(now.get().plus(duration));
    }
}
