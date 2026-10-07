package com.dadcoach.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Filter chains matched on real URL paths (playbook §48, Tair's SecurityConfig). Each service surface has its own
 * key and its own chain; an unconfigured key opens nothing (fail closed).
 *
 * <p>Orders 1-9 belong to this class and the final catch-all is {@link Ordered#LOWEST_PRECEDENCE}. The dashboard's
 * chains ({@code /api/auth/**}, {@code /api/me/**}, {@code /api/father/**}, {@code /api/admin/**},
 * {@code /api/ops/**}, {@code /api/public/**}) are declared by their own packages with orders 20-99.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Platform → Dad Coach: the AI tools and context providers (TOOL_API_KEY). */
    @Bean
    @Order(1)
    SecurityFilterChain toolsAndContext(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
                                        @Value("${tool-api.api-key:}") String toolKey) throws Exception {
        return serviceChain(http, entryPoint, toolKey, "platform", "/api/tools/**", "/api/context/**");
    }

    /** Platform → Dad Coach: the scheduled-response callback (WORKFLOW_PLATFORM_CALLBACK_API_KEY). */
    @Bean
    @Order(2)
    SecurityFilterChain integration(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
                                    @Value("${workflow.platform.scheduled-response.api-key:}") String callbackKey,
                                    @Value("${workflow.platform.scheduled-response.enabled:false}") boolean callbackEnabled)
            throws Exception {
        return serviceChain(http, entryPoint, callbackEnabled ? callbackKey : "", "platform-callback", "/api/integration/**");
    }

    /** Operators: the father admin API (list, read, delete for good) with DADCOACH_ADMIN_API_KEY. */
    @Bean
    @Order(3)
    SecurityFilterChain operatorApi(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
                                    @Value("${dad-coach.security.admin-api-key:}") String adminKey) throws Exception {
        return serviceChain(http, entryPoint, adminKey, "operator", "/api/v1/admin/**");
    }

    /** Meta's webhook: open here; the controller verifies X-Hub-Signature-256 over the raw body. */
    @Bean
    @Order(4)
    SecurityFilterChain whatsappWebhook(HttpSecurity http) throws Exception {
        http.securityMatcher("/webhook/whatsapp", "/webhook/whatsapp/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** The two Google Calendar OAuth hops: both verify an HMAC signature (CalendarLinkSigner) themselves. */
    @Bean
    @Order(5)
    SecurityFilterChain calendarOAuth(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/calendar/connect/*", "/api/v1/calendar/callback")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** Everything no other chain claimed: only the health probes; the rest is refused. */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain rest(HttpSecurity http, RestAuthenticationEntryPoint entryPoint) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")
                        .permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    private static SecurityFilterChain serviceChain(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
                                                    String key, String principal, String... paths) throws Exception {
        http.securityMatcher(paths)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthenticationFilter(key, principal), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
