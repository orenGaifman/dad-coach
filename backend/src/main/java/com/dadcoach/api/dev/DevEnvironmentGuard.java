package com.dadcoach.api.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Centralized environment detection component that determines if dev endpoints are allowed.
 *
 * <p>This guard implements a fail-secure approach where access is blocked by default
 * if the environment cannot be determined.</p>
 *
 * <p>Priority for determining access:</p>
 * <ol>
 *   <li>If {@code dadcoach.dev.enabled} is explicitly set, use that value</li>
 *   <li>Otherwise, block if Spring profile is "prod" or "production" (case-insensitive)</li>
 *   <li>Allow for all other profiles (dev, local, staging, test, qa, etc.)</li>
 * </ol>
 *
 * @see DevEndpointsDisabledException
 */
@Component
public class DevEnvironmentGuard {

    private static final Logger log = LoggerFactory.getLogger(DevEnvironmentGuard.class);

    private final Boolean devEnabled;
    private final Environment environment;

    public DevEnvironmentGuard(
            @Value("${dadcoach.dev.enabled:#{null}}") Boolean devEnabled,
            Environment environment) {
        this.devEnabled = devEnabled;
        this.environment = environment;
    }

    /**
     * Determines if dev endpoints are allowed in the current environment.
     *
     * <p>Priority:</p>
     * <ol>
     *   <li>If dadcoach.dev.enabled is explicitly set, use that value</li>
     *   <li>Otherwise, block if Spring profile is "prod" or "production"</li>
     *   <li>Allow for all other profiles (dev, local, staging, test, qa)</li>
     * </ol>
     *
     * @return true if dev endpoints should be allowed
     */
    public boolean isDevAllowed() {
        try {
            // Explicit configuration takes precedence
            if (devEnabled != null) {
                return devEnabled;
            }

            // Check Spring profiles
            String[] activeProfiles = environment.getActiveProfiles();
            for (String profile : activeProfiles) {
                if ("prod".equalsIgnoreCase(profile) ||
                    "production".equalsIgnoreCase(profile)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            // Security precaution: block access if we can't determine environment
            log.error("Failed to determine environment, blocking dev access", e);
            return false;
        }
    }

    /**
     * Throws DevEndpointsDisabledException if dev access is not allowed.
     *
     * @throws DevEndpointsDisabledException if dev access is blocked
     */
    public void requireDevAccess() {
        if (!isDevAllowed()) {
            log.warn("Dev endpoint access rejected in production environment");
            throw new DevEndpointsDisabledException();
        }
    }
}
