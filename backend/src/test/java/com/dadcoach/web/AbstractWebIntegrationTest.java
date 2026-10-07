package com.dadcoach.web;

import com.dadcoach.auth.LoginLinkRateLimiter;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The whole application on ONE shared static Postgres (Tair / Big Boss AbstractIntegrationTest; deliberately no
 * {@code @Testcontainers}, no {@code @MockBean} - one Spring context for every test class). The database starts as
 * production's schema at V22 with its Flyway history (test resource db/prod-v22-baseline.sql), and Flyway applies the
 * repository's newer migrations on top - exactly what a deploy does.
 *
 * <p>Fathers are created per test on +1999 numbers ({@link #newFather}); nothing depends on test order or on the
 * time of day (the weekly plan is computed from the real clock, seeds are relative to now).</p>
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "dadcoach.dashboard.ops-api-key=test-ops-key-0123456789abcdef",
        "dadcoach.dashboard.cookie-secure=false",
        "dadcoach.dashboard.whatsapp-public-number=+19995550100",
        "dad-coach.web.base-url=http://localhost:5390",
        "workflow.platform.enabled=false",
        "dadcoach.features.morning-reminders=false"
})
@AutoConfigureMockMvc
public abstract class AbstractWebIntegrationTest {

    public static final String OPS_KEY = "test-ops-key-0123456789abcdef";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("db/prod-v22-baseline.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected LoginLinkRateLimiter rateLimiter;

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    @BeforeEach
    void resetRateLimits() {
        rateLimiter.reset();
    }

    /** A unique +1999 test number. */
    protected static String testPhone() {
        long n = Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000_000L);
        return String.format("+1999%07d", n);
    }

    /** A father (ACTIVE unless said otherwise), as onboarding leaves him. */
    protected long newFather(String name, String status) {
        String phone = testPhone();
        jdbc.update("INSERT INTO father (phone, display_name, status, timezone, current_belt) VALUES (?, ?, ?, ?, 'WHITE')",
                phone, name, status, middayZone());
        return jdbc.queryForObject("SELECT id FROM father WHERE phone = ?", Long.class, phone);
    }

    /**
     * A UTC offset where it is about noon right now (Big Boss TestZones.midday): sessions a few hours either side of
     * now stay on the father's same day and week, whenever the suite runs.
     */
    protected static String middayZone() {
        int utcHour = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).getHour();
        int offset = 12 - utcHour;
        if (offset > 14) {
            offset -= 24;
        }
        if (offset < -12) {
            offset += 24;
        }
        return java.time.ZoneOffset.ofHours(offset).getId().replace("Z", "+00:00");
    }

    protected long newFather(String name) {
        return newFather(name, "ACTIVE");
    }

    protected String phoneOf(long fatherId) {
        return jdbc.queryForObject("SELECT phone FROM father WHERE id = ?", String.class, fatherId);
    }

    protected long newChild(long fatherId, String name, int age) {
        return jdbc.queryForObject("INSERT INTO child (father_id, name, birth_date) VALUES (?, ?, current_date - (? * interval '1 year')) RETURNING id",
                Long.class, fatherId, name, age);
    }

    /** A session relative to now (minutes may be negative: in the past). */
    protected UUID newSession(long fatherId, long childId, long startInMinutes, int durationMinutes, String status) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.MINUTES).plus(startInMinutes, ChronoUnit.MINUTES);
        return jdbc.queryForObject("""
                INSERT INTO quality_time (father_id, child_id, scheduled_start, scheduled_end, status)
                VALUES (?, ?, ?, ?, ?) RETURNING id""", UUID.class, fatherId, childId,
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plus(durationMinutes, ChronoUnit.MINUTES)), status);
    }

    /** An ops-issued login link's raw token for this phone. */
    protected String issueToken(String phone) throws Exception {
        MvcResult r = mvc.perform(post("/api/ops/login-links").header("X-API-Key", OPS_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}")).andReturn();
        Matcher m = TOKEN.matcher(r.getResponse().getContentAsString());
        if (!m.find()) {
            throw new AssertionError("no login link for " + phone + ": " + r.getResponse().getStatus());
        }
        return m.group(1);
    }

    /** A signed-in browser: its session cookie and CSRF cookie. */
    protected Browser signIn(String phone) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/consume-link").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + issueToken(phone) + "\"}")).andReturn();
        Cookie session = r.getResponse().getCookie("DADCOACH_SESSION");
        if (session == null) {
            throw new AssertionError("no session for " + phone + ": " + r.getResponse().getStatus());
        }
        return new Browser(session.getValue(), UUID.randomUUID().toString());
    }

    protected Browser signInFather(long fatherId) throws Exception {
        return signIn(phoneOf(fatherId));
    }

    protected String newStaff(String name) throws Exception {
        String phone = testPhone();
        mvc.perform(post("/api/ops/bootstrap-admin").header("X-API-Key", OPS_KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"name\":\"" + name + "\"}"));
        return phone;
    }

    /** A browser's cookies; {@link #on} adds them (and the CSRF echo) to a request. */
    public record Browser(String session, String csrf) {

        public MockHttpServletRequestBuilder on(MockHttpServletRequestBuilder request) {
            return request.cookie(new Cookie("DADCOACH_SESSION", session), new Cookie("DADCOACH_XSRF", csrf))
                    .header("X-XSRF-TOKEN", csrf);
        }

        /** The session cookie only - a write without the CSRF echo. */
        public MockHttpServletRequestBuilder withoutCsrf(MockHttpServletRequestBuilder request) {
            return request.cookie(new Cookie("DADCOACH_SESSION", session));
        }
    }
}
