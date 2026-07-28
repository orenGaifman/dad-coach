package com.dadcoach.workspace.aggregation;

import com.dadcoach.father.CoachingPhase;
import com.dadcoach.father.CoachingStyle;
import com.dadcoach.father.FatherStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only model representing father data needed by the workspace aggregation layer.
 *
 * <p>This is NOT a JPA entity — it is a projection/DTO used to decouple the workspace
 * read layer from the Father domain entity's internal structure.</p>
 */
public record FatherReadModel(
        UUID fatherId,
        String displayName,
        String phone,
        String timezone,
        CoachingStyle coachingStyle,
        String preferredCoachingTime,
        String languagePreference,
        CoachingPhase coachingPhase,
        Instant activatedAt,
        FatherStatus status
) {}
