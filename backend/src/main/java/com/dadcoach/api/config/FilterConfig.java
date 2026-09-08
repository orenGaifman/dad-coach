package com.dadcoach.api.config;

import com.dadcoach.api.context.ContextProviderAuthFilter;
import com.dadcoach.api.profile.ProfileApiAuthFilter;
import com.dadcoach.api.tools.ToolApiAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to disable automatic servlet filter registration for API key filters.
 * <p>
 * The ToolApiAuthFilter, ContextProviderAuthFilter, and ProfileApiAuthFilter are
 * explicitly added to the Spring Security filter chain in SecurityConfig. This
 * configuration prevents them from also being registered as servlet filters,
 * which would cause them to run twice.
 */
@Configuration
public class FilterConfig {

    /**
     * Disable automatic servlet registration for ToolApiAuthFilter.
     * The filter is registered in the Security filter chain instead.
     */
    @Bean
    public FilterRegistrationBean<ToolApiAuthFilter> toolApiAuthFilterRegistration(
            ToolApiAuthFilter filter) {
        FilterRegistrationBean<ToolApiAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Disable automatic servlet registration for ContextProviderAuthFilter.
     * The filter is registered in the Security filter chain instead.
     */
    @Bean
    public FilterRegistrationBean<ContextProviderAuthFilter> contextProviderAuthFilterRegistration(
            ContextProviderAuthFilter filter) {
        FilterRegistrationBean<ContextProviderAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Disable automatic servlet registration for ProfileApiAuthFilter.
     * The filter is registered in the Security filter chain instead.
     */
    @Bean
    public FilterRegistrationBean<ProfileApiAuthFilter> profileApiAuthFilterRegistration(
            ProfileApiAuthFilter filter) {
        FilterRegistrationBean<ProfileApiAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
