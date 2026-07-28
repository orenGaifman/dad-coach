package com.dadcoach.api.config;

import com.dadcoach.api.auth.JwtAuthFilter;
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
 *   <li>{@code /api/v1/admin/**} — requires ADMIN role</li>
 *   <li>{@code /api/v1/service/**} — requires SERVICE role</li>
 *   <li>{@code /api/v1/fathers/me/**} — requires FATHER role</li>
 *   <li>All other requests — authenticated</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
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
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/service/**").hasRole("SERVICE")
                        .requestMatchers("/api/v1/fathers/me/**").hasRole("FATHER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
