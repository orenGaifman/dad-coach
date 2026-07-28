package com.dadcoach.onboarding.localization;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Configuration for the onboarding localization system.
 * Uses ReloadableResourceBundleMessageSource for runtime reload without application restart.
 *
 * Resource bundles:
 *   - i18n/messages   — general system messages
 *   - i18n/wizard     — wizard step-specific content
 *   - i18n/validation — field validation error messages
 *   - i18n/activation — activation flow messages
 *   - i18n/goals      — predefined goal labels
 */
@Configuration
public class LocalizationConfig {

    /**
     * Configures a ReloadableResourceBundleMessageSource that:
     * - Loads from classpath:i18n/ resource bundles
     * - Supports runtime reload (cache 60 seconds in dev, configurable)
     * - Uses UTF-8 encoding for Hebrew support
     * - Falls back to English (default locale) when key not found
     */
    @Bean("onboardingMessageSource")
    public MessageSource onboardingMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(
            "classpath:i18n/messages",
            "classpath:i18n/wizard",
            "classpath:i18n/validation",
            "classpath:i18n/activation",
            "classpath:i18n/goals"
        );
        messageSource.setDefaultEncoding("UTF-8");
        // Cache for 60 seconds — allows runtime reload without restart
        messageSource.setCacheSeconds(60);
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);
        return messageSource;
    }
}
