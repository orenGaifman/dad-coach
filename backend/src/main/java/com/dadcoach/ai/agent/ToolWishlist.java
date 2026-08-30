package com.dadcoach.ai.agent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity for storing AI-suggested tools that don't exist yet.
 * 
 * <p>This implements a "machine learning" style feedback loop where
 * the AI identifies user needs that can't be fulfilled with existing
 * tools and suggests new capabilities.</p>
 * 
 * <p>Workflow:</p>
 * <ol>
 *   <li>User asks for something</li>
 *   <li>AI can't find a matching tool</li>
 *   <li>AI logs a "wish" with suggested tool name and capability</li>
 *   <li>Product team reviews wishes periodically</li>
 *   <li>Popular wishes become new tools</li>
 * </ol>
 */
@Entity
@Table(name = "tool_wishlist", indexes = {
    @Index(name = "idx_tool_wishlist_status", columnList = "status"),
    @Index(name = "idx_tool_wishlist_suggested_name", columnList = "suggested_name"),
    @Index(name = "idx_tool_wishlist_created_at", columnList = "created_at")
})
public class ToolWishlist {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * The AI-suggested name for the new tool.
     * Example: "send_reminder", "get_weather_for_activity"
     */
    @Column(name = "suggested_name", nullable = false, length = 100)
    private String suggestedName;
    
    /**
     * Description of what the user needed.
     * Example: "User asked to send a reminder to their partner about the QT"
     */
    @Column(name = "user_need", nullable = false, columnDefinition = "TEXT")
    private String userNeed;
    
    /**
     * What capability the AI thinks this tool should have.
     * Example: "Send WhatsApp message to a specified phone number"
     */
    @Column(name = "suggested_capability", nullable = false, columnDefinition = "TEXT")
    private String suggestedCapability;
    
    /**
     * The original user message that triggered this wish.
     */
    @Column(name = "original_message", columnDefinition = "TEXT")
    private String originalMessage;
    
    /**
     * The father ID who triggered this wish (for context).
     */
    @Column(name = "father_id")
    private Long fatherId;
    
    /**
     * Status of the wish for review workflow.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WishStatus status = WishStatus.NEW;
    
    /**
     * Priority assigned during review (1=low, 5=critical).
     */
    @Column(name = "priority")
    private Integer priority;
    
    /**
     * Notes from the product team review.
     */
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    
    /**
     * When the wish was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    /**
     * When the wish was last reviewed.
     */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    
    /**
     * Count of similar wishes (for aggregation).
     */
    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount = 1;
    
    /**
     * Status values for the review workflow.
     */
    public enum WishStatus {
        /** Newly created, not yet reviewed */
        NEW,
        /** Under review by product team */
        REVIEWING,
        /** Approved for development */
        APPROVED,
        /** Rejected - not going to implement */
        REJECTED,
        /** Implemented - tool now exists */
        IMPLEMENTED,
        /** Duplicate of another wish */
        DUPLICATE
    }
    
    // ─── Constructors ────────────────────────────────────────────────────────
    
    public ToolWishlist() {
        this.createdAt = Instant.now();
    }
    
    public ToolWishlist(String suggestedName, String userNeed, String suggestedCapability) {
        this();
        this.suggestedName = suggestedName;
        this.userNeed = userNeed;
        this.suggestedCapability = suggestedCapability;
    }
    
    // ─── Builder Pattern ─────────────────────────────────────────────────────
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String suggestedName;
        private String userNeed;
        private String suggestedCapability;
        private String originalMessage;
        private Long fatherId;
        
        public Builder suggestedName(String suggestedName) {
            this.suggestedName = suggestedName;
            return this;
        }
        
        public Builder userNeed(String userNeed) {
            this.userNeed = userNeed;
            return this;
        }
        
        public Builder suggestedCapability(String suggestedCapability) {
            this.suggestedCapability = suggestedCapability;
            return this;
        }
        
        public Builder originalMessage(String originalMessage) {
            this.originalMessage = originalMessage;
            return this;
        }
        
        public Builder fatherId(Long fatherId) {
            this.fatherId = fatherId;
            return this;
        }
        
        public ToolWishlist build() {
            ToolWishlist wish = new ToolWishlist(suggestedName, userNeed, suggestedCapability);
            wish.setOriginalMessage(originalMessage);
            wish.setFatherId(fatherId);
            return wish;
        }
    }
    
    // ─── Getters and Setters ─────────────────────────────────────────────────
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getSuggestedName() {
        return suggestedName;
    }
    
    public void setSuggestedName(String suggestedName) {
        this.suggestedName = suggestedName;
    }
    
    public String getUserNeed() {
        return userNeed;
    }
    
    public void setUserNeed(String userNeed) {
        this.userNeed = userNeed;
    }
    
    public String getSuggestedCapability() {
        return suggestedCapability;
    }
    
    public void setSuggestedCapability(String suggestedCapability) {
        this.suggestedCapability = suggestedCapability;
    }
    
    public String getOriginalMessage() {
        return originalMessage;
    }
    
    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }
    
    public Long getFatherId() {
        return fatherId;
    }
    
    public void setFatherId(Long fatherId) {
        this.fatherId = fatherId;
    }
    
    public WishStatus getStatus() {
        return status;
    }
    
    public void setStatus(WishStatus status) {
        this.status = status;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public String getReviewNotes() {
        return reviewNotes;
    }
    
    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getReviewedAt() {
        return reviewedAt;
    }
    
    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
    
    public int getOccurrenceCount() {
        return occurrenceCount;
    }
    
    public void setOccurrenceCount(int occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }
    
    /**
     * Increment the occurrence count (for similar wishes).
     */
    public void incrementOccurrence() {
        this.occurrenceCount++;
    }
    
    @Override
    public String toString() {
        return "ToolWishlist{" +
                "id=" + id +
                ", suggestedName='" + suggestedName + '\'' +
                ", status=" + status +
                ", occurrenceCount=" + occurrenceCount +
                ", createdAt=" + createdAt +
                '}';
    }
}
