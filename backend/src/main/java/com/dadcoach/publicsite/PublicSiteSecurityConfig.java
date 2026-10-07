package com.dadcoach.publicsite;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Its own filter chain for /api/public/** (ordered before the main chain in api.config.SecurityConfig, which
 * therefore never sees these paths): stateless, no CSRF (no cookies or credentials are involved), CORS only
 * for the marketing site's origin(s) from SITE_ORIGINS (comma-separated; empty = no cross-origin caller).
 * Only POST /api/public/site-signups is open; anything else under /api/public is denied.
 * The CORS source is built here and deliberately NOT a bean, so it cannot replace the main chain's.
 */
@Configuration
public class PublicSiteSecurityConfig {

    static final String SIGNUPS = "/api/public/site-signups";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public SecurityFilterChain publicSiteFilterChain(HttpSecurity http,
            @Value("${dad-coach.public-site.origins:${SITE_ORIGINS:}}") String siteOrigins) throws Exception {
        List<String> origins = Arrays.stream(siteOrigins.split(",")).map(String::strip).filter(o -> !o.isEmpty()).toList();
        UrlBasedCorsConfigurationSource cors = new UrlBasedCorsConfigurationSource();
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("POST", "OPTIONS"));
            config.setAllowedHeaders(List.of("Content-Type"));
            config.setAllowCredentials(false);
            config.setMaxAge(3600L);
            cors.registerCorsConfiguration(SIGNUPS, config);
        }
        return http
                .securityMatcher("/api/public/**")
                .cors(c -> c.configurationSource(cors))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, SIGNUPS).permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
