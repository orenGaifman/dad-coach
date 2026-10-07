package com.dadcoach.auth;

import com.dadcoach.web.common.ApiError;
import com.dadcoach.config.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * The dashboard's own filter chains (D-004/D-005), ordered before the legacy chain (api/config/SecurityConfig, which
 * keeps every other path). Matched on real HTTP paths:
 * <ol>
 *   <li>{@code /api/ops/**} - the operator key (DADCOACH_OPS_API_KEY); unset = closed.</li>
 *   <li>{@code /api/auth/request-link}, {@code /api/auth/consume-link}, {@code /api/auth/sign-in-info} - how a
 *       session begins: open.</li>
 *   <li>{@code /api/auth/**}, {@code /api/me}, {@code /api/father/**}, {@code /api/admin/**} - the session cookie +
 *       CSRF double submit (cookie DADCOACH_XSRF, header X-XSRF-TOKEN). Area checks (father / staff) are in the
 *       controllers, so a wrong area is a coded 403 and someone else's object a 404.</li>
 * </ol>
 * {@code /api/admin/test/**} (the legacy operator tool console, admin key) stays on the legacy chain.
 */
@Configuration
public class DashboardSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain dashboardOps(HttpSecurity http, DashboardProperties properties, ObjectMapper json) throws Exception {
        http.securityMatcher("/api/ops/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthenticationFilter(properties.getOpsApiKey()), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(ApiKeyAuthenticationFilter.ROLE_OPS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> write(res, json, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, e) -> write(res, json, 401, "UNAUTHENTICATED")));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain dashboardPreAuth(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/auth/request-link", "/api/auth/consume-link", "/api/auth/sign-in-info")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain dashboardBrowser(HttpSecurity http, SessionService sessions, SessionCookies cookies,
                                         ObjectMapper json) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName(SessionCookies.CSRF_COOKIE);
        csrfRepository.setHeaderName(SessionCookies.CSRF_HEADER);
        csrfRepository.setCookiePath("/");
        csrfRepository.setCookieCustomizer(c -> c.sameSite("Lax").secure(cookies.secure()));
        http.securityMatcher(browserPaths())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // The SPA echoes the raw cookie value; the plain handler validates that raw value.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // No HttpSession: rotation-on-login would delete the cookie on every request (BB D-024).
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .cors(cors -> cors.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new SessionCookieAuthenticationFilter(sessions, cookies), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), SessionCookieAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> write(res, json, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, e) -> write(res, json, 403,
                                e instanceof org.springframework.security.web.csrf.CsrfException ? "CSRF_REJECTED" : "FORBIDDEN")));
        return http.build();
    }

    static RequestMatcher browserPaths() {
        RequestMatcher admin = new AndRequestMatcher(new AntPathRequestMatcher("/api/admin/**"),
                new NegatedRequestMatcher(new AntPathRequestMatcher("/api/admin/test/**")));
        return new OrRequestMatcher(
                new AntPathRequestMatcher("/api/auth/**"),
                new AntPathRequestMatcher("/api/me"),
                new AntPathRequestMatcher("/api/me/**"),
                new AntPathRequestMatcher("/api/father/**"),
                admin);
    }

    private static void write(HttpServletResponse response, ObjectMapper json, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(new ApiError(Instant.now(), status, code,
                status == 401 ? "authentication required" : "forbidden", CorrelationIdFilter.currentCorrelationId(), List.of())));
    }
}
