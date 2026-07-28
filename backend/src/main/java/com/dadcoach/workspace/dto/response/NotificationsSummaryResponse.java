package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the notifications endpoint (GET /api/v1/workspace/notifications).
 *
 * <p>Returns unread count, total 30-day count, and paginated notification list.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationsSummaryResponse(
        @JsonProperty("unread_count") int unreadCount,
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("notifications") List<NotificationItem> notifications
) {

    /**
     * Represents an individual notification item.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationItem(
            @JsonProperty("notification_id") UUID notificationId,
            @JsonProperty("type") String type,
            @JsonProperty("title") String title,
            @JsonProperty("body") String body,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("read_at") Instant readAt,
            @JsonProperty("action_url") String actionUrl,
            @JsonProperty("priority") String priority
    ) {}
}
