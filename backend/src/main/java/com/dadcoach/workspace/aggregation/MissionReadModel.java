package com.dadcoach.workspace.aggregation;

import com.dadcoach.mission.MissionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only model representing mission data needed by the workspace aggregation layer.
 *
 * // TODO: Wire to actual implementation from SPEC-002/SPEC-007 when available
 */
public record MissionReadModel(
        UUID missionId,
        UUID fatherId,
        UUID childId,
        String title,
        String description,
        MissionStatus status,
        Instant createdAt,
        Instant completedAt
) {}
