package com.dadcoach.api.father;

import com.dadcoach.father.CoachingStyle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for PUT /api/v1/fathers/me — updating father preferences.
 * <p>
 * All fields are optional (partial update). Only non-null fields are applied.
 * <p>
 * Validation rules:
 * <ul>
 *   <li>timezone: must be a valid IANA timezone identifier (validated at service layer)</li>
 *   <li>coachingStyle: must be one of GENTLE, BALANCED, DIRECT, MOTIVATIONAL</li>
 *   <li>preferredCoachingTime: must be in HH:MM format (24-hour)</li>
 * </ul>
 */
public record FatherUpdateRequest(

        @Pattern(regexp = "^[A-Za-z_]+/[A-Za-z_]+$",
                message = "Timezone must be a valid IANA timezone (e.g., 'America/New_York')")
        String timezone,

        CoachingStyle coachingStyle,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                message = "Preferred coaching time must be in HH:MM format (24-hour)")
        String preferredCoachingTime
) {
}
