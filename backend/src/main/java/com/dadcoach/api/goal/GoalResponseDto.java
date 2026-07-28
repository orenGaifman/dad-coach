package com.dadcoach.api.goal;

import com.dadcoach.domain.goal.Goal;
import com.dadcoach.goal.GoalCategory;

import java.time.Instant;

/**
 * Response DTO for Goal resources exposed via the Father API.
 *
 * <p>Includes progress percentage to show coaching advancement.
 * Internal fields (estimated_total_missions internals) are not exposed.</p>
 */
public class GoalResponseDto {

    private Long id;
    private String title;
    private String description;
    private GoalCategory category;
    private int priority;
    private String status;
    private int progressPercentage;
    private Instant createdAt;
    private Instant completedAt;

    public GoalResponseDto() {
    }

    /**
     * Maps a Goal entity to the API response DTO.
     */
    public static GoalResponseDto fromEntity(Goal goal) {
        GoalResponseDto dto = new GoalResponseDto();
        dto.setId(goal.getId());
        dto.setTitle(goal.getTitle());
        dto.setDescription(goal.getDescription());
        dto.setCategory(goal.getCategory());
        dto.setPriority(goal.getPriority());
        dto.setStatus(goal.getStatus());
        dto.setProgressPercentage(goal.getProgressPercentage());
        dto.setCreatedAt(goal.getCreatedAt());
        dto.setCompletedAt(goal.getCompletedAt());
        return dto;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GoalCategory getCategory() {
        return category;
    }

    public void setCategory(GoalCategory category) {
        this.category = category;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(int progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
