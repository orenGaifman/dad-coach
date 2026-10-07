package com.dadcoach.publicsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.config.ClockConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The marketing site's signup endpoint on a real Postgres 16, through the real web + security stack of this package
 * (its own /api/public/** filter chain, CORS from SITE_ORIGINS), with V40 exactly as shipped.
 *
 * <p>One static container for the JVM (the singleton pattern of Tair's AbstractIntegrationTest: never
 * {@code @Container}, which would stop it after the first class). No {@code @MockBean}. The context is this package
 * only, because the repository's migration chain cannot yet build an empty database (V1-V9 are missing from the
 * repo; WS-A restores a baseline). When it can, this class moves onto the shared full-context base class and Flyway
 * applies V40 like every other migration.
 */
@SpringBootTest(classes = SiteSignupIntegrationTest.PublicSiteTestApp.class, properties = {
        "dad-coach.public-site.origins=https://www.dad-coach.example, https://site.dad-coach.test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "management.endpoint.health.validate-group-membership=false"
})
@AutoConfigureMockMvc
class SiteSignupIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = SiteSignupService.class)
    @Import(ClockConfig.class)
    static class PublicSiteTestApp {
    }

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("dadcoach").withUsername("dadcoach").withPassword("dadcoach_test");

    static {
        POSTGRES.start();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V40__site_signup.sql"), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("could not apply V40", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final String SITE = "https://www.dad-coach.example";
    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SiteSignupService signups;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM site_signup");
    }

    /** Every test (and every burst) gets its own client address, so the per-IP limiter never couples tests. */
    private static String newClient() {
        return "10.40.0." + CLIENTS.incrementAndGet();
    }

    private static MockHttpServletRequestBuilder signup(String json, String client) {
        return post("/api/public/site-signups").header("Origin", SITE).header("X-Forwarded-For", client + ", 10.0.0.1")
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String body(String name, String phone, String website) {
        return "{\"name\":\"" + name + "\",\"phone\":\"" + phone + "\",\"source\":\"site\",\"page\":\"/\",\"website\":\"" + website + "\"}";
    }

    private Integer rows() {
        return jdbc.queryForObject("SELECT count(*) FROM site_signup", Integer.class);
    }

    @Test
    void aSignupIsStoredWithItsPhoneInE164AndTheSiteMayCallCrossOrigin() throws Exception {
        mvc.perform(signup(body("  אורי   כהן ", "050-123 4567", ""), newClient()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(header().string("Access-Control-Allow-Origin", SITE));

        assertThat(jdbc.queryForObject("SELECT name || '|' || phone || '|' || source || '|' || page || '|' || submissions FROM site_signup",
                String.class)).isEqualTo("אורי כהן|+972501234567|site|/|1");
    }

    @Test
    void aRepeatFromTheSamePhoneUpdatesTheRowInsteadOfAddingOne() throws Exception {
        String client = newClient();
        mvc.perform(signup(body("אורי", "+972 50-123-4567", ""), client)).andExpect(status().isAccepted());
        mvc.perform(signup(body("אורי כהן", "0501234567", ""), client)).andExpect(status().isAccepted());

        assertThat(rows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name || '|' || submissions FROM site_signup WHERE phone = '+972501234567'", String.class))
                .isEqualTo("אורי כהן|2");
    }

    @Test
    void honeypotInvalidAndMalformedSubmissionsAreNotStoredButLookTheSame() throws Exception {
        mvc.perform(signup(body("בוט", "0521112233", "https://spam.example"), newClient())).andExpect(status().isAccepted());
        mvc.perform(signup(body("דני", "03-1234567", ""), newClient())).andExpect(status().isAccepted()); // landline: no WhatsApp
        mvc.perform(signup(body("ד", "0521112233", ""), newClient())).andExpect(status().isAccepted());   // name too short
        mvc.perform(signup("{\"phone\":\"0521112233\"}", newClient())).andExpect(status().isAccepted());   // no name

        assertThat(rows()).isZero();
    }

    @Test
    void moreThanFiveFromOneAddressInTenMinutesAreDroppedSilently() throws Exception {
        String client = newClient();
        for (int i = 0; i < 6; i++) {
            mvc.perform(signup(body("אבא " + i, "05411100" + (10 + i), ""), client)).andExpect(status().isAccepted());
        }
        assertThat(rows()).isEqualTo(5);
        // another address is not affected
        mvc.perform(signup(body("אבא אחר", "0541119999", ""), newClient())).andExpect(status().isAccepted());
        assertThat(rows()).isEqualTo(6);
    }

    @Test
    void onlyTheSiteOriginsGetCorsAndNothingElseUnderPublicIsOpen() throws Exception {
        mvc.perform(options("/api/public/site-signups").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/public/site-signups").header("Origin", "https://site.dad-coach.test")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://site.dad-coach.test"));
        mvc.perform(post("/api/public/site-signups").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(body("אורי", "0501234567", "")))
                .andExpect(status().isForbidden());
        assertThat(rows()).isZero();

        // reading the list is not public (the admin reads it through SiteSignupService)
        mvc.perform(get("/api/public/site-signups")).andExpect(status().isForbidden());
        mvc.perform(post("/api/public/anything-else").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recentSignupsAreNewestFirstWithinTheWindowAndCanBeDeletedByPhone() throws Exception {
        mvc.perform(signup(body("ראשון", "0501110001", ""), newClient())).andExpect(status().isAccepted());
        mvc.perform(signup(body("שני", "0501110002", ""), newClient())).andExpect(status().isAccepted());
        jdbc.update("UPDATE site_signup SET last_submitted_at = ? WHERE phone = '+972501110001'",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))));
        jdbc.update("INSERT INTO site_signup (phone, name, first_submitted_at, last_submitted_at) VALUES ('+972501110003', 'ישן', ?, ?)",
                Timestamp.from(Instant.now().minus(Duration.ofDays(40))), Timestamp.from(Instant.now().minus(Duration.ofDays(40))));

        List<SiteSignup> recent = signups.recentSignups(30);
        assertThat(recent).extracting(SiteSignup::name).containsExactly("שני", "ראשון");
        assertThat(recent.get(0).phone()).isEqualTo("+972501110002");
        assertThat(recent.get(0).source()).isEqualTo("site");
        assertThat(signups.recentSignups(365)).hasSize(3);

        assertThat(signups.deleteByPhone("+972501110002")).isEqualTo(1);
        assertThat(signups.recentSignups(30)).extracting(SiteSignup::name).containsExactly("ראשון");
    }

    @Test
    void israeliMobileNormalization() {
        assertThat(IsraeliMobile.toE164("050-1234567")).contains("+972501234567");
        assertThat(IsraeliMobile.toE164("+972 (0)50 123 4567")).contains("+972501234567");
        assertThat(IsraeliMobile.toE164("00972-54-1234567")).contains("+972541234567");
        assertThat(IsraeliMobile.toE164("972521234567")).contains("+972521234567");
        assertThat(IsraeliMobile.toE164("02-1234567")).isEmpty();
        assertThat(IsraeliMobile.toE164("+15551234567")).isEmpty();
        assertThat(IsraeliMobile.toE164("05012345")).isEmpty();
        assertThat(IsraeliMobile.toE164(null)).isEmpty();
    }
}
