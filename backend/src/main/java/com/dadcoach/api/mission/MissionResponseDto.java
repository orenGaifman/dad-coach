package com.dadcoach.api.mission;

import com.dadcoach.domain.mission.Mission;
import com.dadcoach.mission.MissionStatus;

import java.time.Instant;

/**
 * Response DTO for Mission resources exposed via the Father API.
 *
 * <p>Missions are read-only from the Father API perspective — they are created
 * exclusively by the coaching orchestration pipeline (SPEC-005).</p>
 *
 * <p>Includes outcome_rating if the mission is completed, and expiration
 * information for time-sensitive missions.</p>
 */
public class MissionResponseDto {

    private Long id;
    private Long childId;
    private Long goalId;
    private String title;
    private String description;
    private String category;
    private int difficulty;
    private int estimatedMinutes;
    private MissionStatus status;
    private Integer outcomeRating;
    private String outcomeNotes;
    private Instant assignedAt;
    private Instant expiresAt;
    private Instant acceptedAt;
    private Instant completedAt;

    public MissionResponseDto() {
    }

    /**
     * Maps a Mission entity to the API response DTO.
     * Internal fields like promptVersion are not exposed.
     */
    public static MissionResponseDto fromEntity(Mission mission) {
        MissionResponseDto dto = new MissionResponseDto();
        dto.setId(mission.getId());
        dto.setChildId(mission.getChildId());
        dto.setGoalId(mission.getGoalId());
        dto.setTitle(mission.getTitle());
        dto.setDescription(mission.getDescription());
        dto.setCategory(mission.getCategory());
        dto.setDifficulty(mission.getDifficulty());
        dto.setEstimatedMinutes(mission.getEstimatedMinutes());
        dto.setStatus(mission.getStatus());
        dto.setOutcomeRating(mission.getOutcomeRating());
        dto.setOutcomeNotes(mission.getOutcomeNotes());
        dto.setAssignedAt(mission.getAssignedAt());
        dto.setExpiresAt(mission.getExpiresAt());
        dto.setAcceptedAt(mission.getAcceptedAt());
        dto.setCompletedAt(mission.getCompletedAt());
        return dto;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChildId() {
        return childId;
    }

    public void setChildId(Long childId) {
        this.childId = childId;
    }

    public Long getGoalId() {
        return goalId;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(int estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public Integer getOutcomeRating() {
        return outcomeRating;
    }

    public void setOutcomeRating(Integer outcomeRating) {
        this.outcomeRating = outcomeRating;
    }

    public String getOutcomeNotes() {
        return outcomeNotes;
    }

    public void setOutcomeNotes(String outcomeNotes) {
        this.outcomeNotes = outcomeNotes;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
