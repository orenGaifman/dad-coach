package com.dadcoach.workspace.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for marking specific notifications as read.
 */
public record MarkNotificationsReadRequest(
        @JsonProperty("notification_ids")
        @NotEmpty(message = "notification_ids must not be empty")
        List<UUID> notificationIds
) {}
