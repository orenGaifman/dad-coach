package com.dadcoach.integration.platform;

import com.dadcoach.common.AppConstants;
import com.dadcoach.domain.father.Father;
import java.time.DateTimeException;
import java.time.ZoneId;

/** The father's timezone as the platform must know it: his stored zone when valid, else Asia/Jerusalem. */
public final class FatherTimezones {

    private FatherTimezones() {
    }

    public static ZoneId of(Father father) {
        String tz = father == null ? null : father.getTimezone();
        if (tz != null && !tz.isBlank()) {
            try {
                return ZoneId.of(tz.trim());
            } catch (DateTimeException ignored) {
                // fall through: an unknown zone never reaches the platform
            }
        }
        return AppConstants.DEFAULT_ZONE_ID;
    }
}
