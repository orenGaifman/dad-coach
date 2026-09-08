package com.dadcoach.api.config;

import com.dadcoach.api.auth.JwtAuthFilter;
import com.dadcoach.api.context.ContextProviderAuthFilter;
import com.dadcoach.api.profile.ProfileApiAuthFilter;
import com.dadcoach.api.tools.ToolApiAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration with JWT-based authentication and route guards.
 * <p>
 * Route access rules:
 * <ul>
 *   <li>{@code /actuator/health/**} — public (liveness/readiness probes)</li>
 *   <li>{@code /webhook/**} — public (provider webhooks use their own signature verification)</li>
 *   <li>{@code /api/tools/**} — permitAll (uses X-API-Key via ToolApiAuthFilter)</li>
 *   <li>{@code /api/context/**} — permitAll (uses X-API-Key via ContextProviderAuthFilter)</li>
 *   <li>{@code /api/profile/**} — permitAll (uses X-API-Key via ProfileApiAuthFilter)</li>
 *   <li>{@code /api/webhooks/**} — permitAll (uses X-API-Key via webhook auth)</li>
 *   <li>{@code /api/v1/admin/**} — requires ADMIN role</li>
 *   <li>{@code /api/v1/service/**} — requires SERVICE role</li>
 *   <li>{@code /api/v1/fathers/me/**} — requires FATHER role</li>
 *   <li>All other requests — authenticated</li>
 * </ul>
 * <p>
 * Filter chain order:
 * <ol>
 *   <li>ToolApiAuthFilter - handles /api/tools/** with X-API-Key</li>
 *   <li>ContextProviderAuthFilter - handles /api/context/** with X-API-Key</li>
 *   <li>ProfileApiAuthFilter - handles /api/profile/** with X-API-Key</li>
 *   <li>JwtAuthFilter - handles JWT Bearer token authentication</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ToolApiAuthFilter toolApiAuthFilter;
    private final ContextProviderAuthFilter contextProviderAuthFilter;
    private final ProfileApiAuthFilter profileApiAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          ToolApiAuthFilter toolApiAuthFilter,
                          ContextProviderAuthFilter contextProviderAuthFilter,
                          ProfileApiAuthFilter profileApiAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.toolApiAuthFilter = toolApiAuthFilter;
        this.contextProviderAuthFilter = contextProviderAuthFilter;
        this.profileApiAuthFilter = profileApiAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/problem+json");
                            response.getWriter().write("""
                                    {
                                      "type": "https://dadcoach.app/errors/UNAUTHORIZED",
                                      "title": "Authentication Required",
                                      "status": 401,
                                      "detail": "Authentication credentials are required",
                                      "error_code": "UNAUTHORIZED",
                                      "retryable": false
                                    }
                                    """);
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Health check endpoints (liveness/readiness probes)
                        .requestMatchers("/actuator/health/**").permitAll()
                        // WhatsApp webhooks (use their own signature verification)
                        .requestMatchers("/webhook/**").permitAll()
                        // Tool API endpoints (use X-API-Key via ToolApiAuthFilter)
                        .requestMatchers("/api/tools/**").permitAll()
                        // Context Provider API endpoints (use X-API-Key via ContextProviderAuthFilter)
                        .requestMatchers("/api/context/**").permitAll()
                        // Profile API endpoints (use X-API-Key via ProfileApiAuthFilter)
                        .requestMatchers("/api/profile/**").permitAll()
                        // Platform webhook endpoints (use X-API-Key auth)
                        .requestMatchers("/api/webhooks/**").permitAll()
                        // Onboarding endpoints (no auth required - uses invitation tokens)
                        .requestMatchers("/api/v1/onboarding/**").permitAll()
                        .requestMatchers("/api/v1/invitations/**").permitAll()
                        .requestMatchers("/api/v1/activation/**").permitAll()
                        // Calendar OAuth endpoints (public - OAuth flow handles auth)
                        .requestMatchers("/api/v1/calendar/**").permitAll()
                        // Magic link auth (token is the credential)
                        .requestMatchers("/api/v1/auth/magic-link/**").permitAll()
                        // TODO: SECURITY - Admin endpoints are currently public.
                        // Production deployment should implement admin authentication before enabling.
                        // Options: OAuth2, API key validation, or IP whitelist.
                        .requestMatchers("/api/v1/admin/**").permitAll()
                        // Dev endpoints (protected by DevEnvironmentGuard - blocks in production)
                        .requestMatchers("/api/v1/dev/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                // Add API key filters before JWT filter to handle /api/tools/**, /api/context/**, and /api/profile/**
                .addFilterBefore(toolApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(contextProviderAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(profileApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
