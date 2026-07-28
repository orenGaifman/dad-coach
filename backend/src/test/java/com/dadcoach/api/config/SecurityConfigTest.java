package com.dadcoach.api.config;

import com.dadcoach.api.auth.JwtAuthFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {
        SecurityConfig.class,
        CorsConfig.class,
        JwtAuthFilter.class,
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        SecurityConfigTest.TestControllers.class
})
@TestPropertySource(properties = {
        "dad-coach.security.jwt.secret=test-secret-key-that-is-at-least-256-bits-long-for-hs256!!",
        "dad-coach.security.jwt.issuer=dad-coach",
        "dad-coach.security.cors.allowed-origins=http://localhost:3000"
})
class SecurityConfigTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hs256!!";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_shouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fathers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_shouldAllowAdminRole() throws Exception {
        String token = buildToken("ADMIN", null);
        mockMvc.perform(get("/api/v1/admin/fathers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_shouldDenyFatherRole() throws Exception {
        String token = buildToken("FATHER", UUID.randomUUID().toString());
        mockMvc.perform(get("/api/v1/admin/fathers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceEndpoint_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/service/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceEndpoint_shouldAllowServiceRole() throws Exception {
        String token = buildToken("SERVICE", null);
        mockMvc.perform(get("/api/v1/service/health")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void serviceEndpoint_shouldDenyFatherRole() throws Exception {
        String token = buildToken("FATHER", UUID.randomUUID().toString());
        mockMvc.perform(get("/api/v1/service/health")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void fatherEndpoint_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/fathers/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fatherEndpoint_shouldAllowFatherRole() throws Exception {
        String token = buildToken("FATHER", UUID.randomUUID().toString());
        mockMvc.perform(get("/api/v1/fathers/me/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void fatherEndpoint_shouldDenyAdminRole() throws Exception {
        String token = buildToken("ADMIN", null);
        mockMvc.perform(get("/api/v1/fathers/me/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String buildToken(String role, String fatherId) {
        var builder = Jwts.builder()
                .subject("test-user")
                .issuer("dad-coach")
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SIGNING_KEY);

        if (fatherId != null) {
            builder.claim("father_id", fatherId);
        }

        return builder.compact();
    }

    @RestController
    static class TestControllers {

        @GetMapping("/actuator/health/liveness")
        public ResponseEntity<String> healthLiveness() {
            return ResponseEntity.ok("{\"status\":\"UP\"}");
        }

        @GetMapping("/api/v1/admin/fathers")
        public ResponseEntity<String> adminFathers() {
            return ResponseEntity.ok("[]");
        }

        @GetMapping("/api/v1/service/health")
        public ResponseEntity<String> serviceHealth() {
            return ResponseEntity.ok("{\"status\":\"UP\"}");
        }

        @GetMapping("/api/v1/fathers/me/profile")
        public ResponseEntity<String> fatherProfile() {
            return ResponseEntity.ok("{\"name\":\"test\"}");
        }
    }
}
