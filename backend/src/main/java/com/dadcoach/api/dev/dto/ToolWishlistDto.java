package com.dadcoach.api.dev.dto;

import com.dadcoach.ai.agent.ToolWishlist;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Tool Wishlist entries in the Dev Dashboard.
 * 
 * <p>Represents AI-suggested tools that don't exist yet,
 * allowing developers to review what capabilities users are asking for.</p>
 */
public record ToolWishlistDto(
    UUID id,
    
    @JsonProperty("suggested_name")
    String suggestedName,
    
    @JsonProperty("user_need")
    String userNeed,
    
    @JsonProperty("suggested_capability")
    String suggestedCapability,
    
    @JsonProperty("original_message")
    String originalMessage,
    
    @JsonProperty("father_id")
    Long fatherId,
    
    String status,
    
    Integer priority,
    
    @JsonProperty("review_notes")
    String reviewNotes,
    
    @JsonProperty("occurrence_count")
    int occurrenceCount,
    
    @JsonProperty("created_at")
    Instant createdAt,
    
    @JsonProperty("reviewed_at")
    Instant reviewedAt
) {
    /**
     * Create DTO from entity.
     */
    public static ToolWishlistDto fromEntity(ToolWishlist entity) {
        return new ToolWishlistDto(
            entity.getId(),
            entity.getSuggestedName(),
            entity.getUserNeed(),
            entity.getSuggestedCapability(),
            entity.getOriginalMessage(),
            entity.getFatherId(),
            entity.getStatus().name(),
            entity.getPriority(),
            entity.getReviewNotes(),
            entity.getOccurrenceCount(),
            entity.getCreatedAt(),
            entity.getReviewedAt()
        );
    }
}
