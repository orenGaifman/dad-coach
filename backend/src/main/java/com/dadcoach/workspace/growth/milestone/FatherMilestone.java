package com.dadcoach.workspace.growth.milestone;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the "father_milestones" table.
 *
 * <p>Records that a specific father has reached a specific milestone.
 * The unique constraint on (father_id, milestone_id) ensures each milestone
 * can only be reached once per father.</p>
 *
 * @see Milestone
 */
@Entity
@Table(name = "father_milestones", uniqueConstraints = {
        @UniqueConstraint(name = "uk_father_milestone", columnNames = {"father_id", "milestone_id"})
})
public class FatherMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "father_id", nullable = false)
    private UUID fatherId;

    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;

    @Column(name = "reached_at", nullable = false)
    private Instant reachedAt;

    /**
     * JPA-required no-arg constructor.
     */
    protected FatherMilestone() {
    }

    /**
     * Creates a new record indicating a father has reached a milestone.
     *
     * @param fatherId    the father's unique identifier
     * @param milestoneId the milestone's unique identifier
     * @param reachedAt   the instant when the milestone was reached
     */
    public FatherMilestone(UUID fatherId, UUID milestoneId, Instant reachedAt) {
        this.fatherId = fatherId;
        this.milestoneId = milestoneId;
        this.reachedAt = reachedAt;
    }

    // ─── Getters ─────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public UUID getMilestoneId() {
        return milestoneId;
    }

    public Instant getReachedAt() {
        return reachedAt;
    }
}
