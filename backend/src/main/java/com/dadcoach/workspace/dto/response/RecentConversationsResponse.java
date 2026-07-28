package com.dadcoach.workspace.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the conversations endpoint (GET /api/v1/workspace/conversations).
 *
 * <p>Returns recent conversations excluding system prompts and AI telemetry.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecentConversationsResponse(
        @JsonProperty("conversations") List<ConversationItem> conversations,
        @JsonProperty("total_count") int totalCount
) {

    /**
     * Represents an individual conversation item.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConversationItem(
            @JsonProperty("conversation_id") UUID conversationId,
            @JsonProperty("type") String type,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("last_message_at") Instant lastMessageAt,
            @JsonProperty("message_count") int messageCount,
            @JsonProperty("summary") String summary,
            @JsonProperty("status") String status
    ) {}
}
