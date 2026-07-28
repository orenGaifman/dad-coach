package com.dadcoach.workspace.aggregation;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only model representing notification data needed by the workspace aggregation layer.
 *
 * // TODO: Wire to actual implementation from SPEC-006 when available
 */
public record NotificationReadModel(
        UUID notificationId,
        UUID fatherId,
        String type,
        String title,
        String body,
        Instant createdAt,
        Instant readAt,
        String actionUrl,
        String priority
) {}
