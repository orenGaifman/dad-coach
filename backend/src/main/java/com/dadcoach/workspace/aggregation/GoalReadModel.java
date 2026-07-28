package com.dadcoach.workspace.aggregation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only model representing goal data needed by the workspace aggregation layer.
 *
 * // TODO: Wire to actual implementation from SPEC-002/SPEC-007 when available
 */
public record GoalReadModel(
        UUID goalId,
        UUID fatherId,
        UUID childId,
        String title,
        String description,
        String category,
        String priority,
        String status,
        int estimatedMissions,
        int completedMissions,
        List<String> milestones,
        List<String> suggestedNextSteps,
        Instant createdAt
) {

    /**
     * Backward-compatible constructor for minimal read model usage.
     */
    public GoalReadModel(UUID goalId, UUID fatherId, UUID childId, String title, String status, Instant createdAt) {
        this(goalId, fatherId, childId, title, null, null, null, status, 10, 0, List.of(), List.of(), createdAt);
    }
}
