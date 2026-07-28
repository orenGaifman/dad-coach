package com.dadcoach.api.father;

import com.dadcoach.father.CoachingPhase;
import com.dadcoach.father.CoachingStyle;
import com.dadcoach.father.FatherStatus;

import java.util.UUID;

/**
 * Response DTO for the Father resource.
 * <p>
 * This DTO contains ONLY public fields that are safe to return to the Father API consumer.
 * The following fields are NEVER included:
 * <ul>
 *   <li>Embeddings (vector data)</li>
 *   <li>AI prompts or internal prompt templates</li>
 *   <li>Raw confidence scores from AI evaluation</li>
 *   <li>Phone number (available only through masked Admin response)</li>
 *   <li>Internal metadata (JSONB blob)</li>
 *   <li>Onboarding state (internal lifecycle tracking)</li>
 * </ul>
 */
public record FatherResponseDto(
        UUID id,
        String displayName,
        String timezone,
        CoachingStyle coachingStyle,
        String preferredCoachingTime,
        FatherStatus status,
        CoachingPhase phase,
        int engagementScore,
        int coachingStreak
) {
}
