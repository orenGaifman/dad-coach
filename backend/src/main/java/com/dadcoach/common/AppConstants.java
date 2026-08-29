/**
 * Copyright 2024 American Well Systems
 * All rights reserved.
 */
package com.dadcoach.common;

import java.time.ZoneId;

/**
 * Application-wide constants.
 * 
 * <p>Centralized location for constants used across multiple services
 * to avoid duplication and ensure consistency.</p>
 */
public final class AppConstants {
    
    private AppConstants() {
        // Utility class - prevent instantiation
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Timezone Constants
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Default timezone for Israel (used when father timezone is not set). */
    public static final String DEFAULT_TIMEZONE = "Asia/Jerusalem";
    
    /** Default timezone as ZoneId for date/time operations. */
    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of(DEFAULT_TIMEZONE);
}
