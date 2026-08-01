package com.dadcoach.domain.mission;

import com.dadcoach.common.InvalidStateTransitionException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.goal.Goal;
import com.dadcoach.mission.MissionStatus;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * JPA entity representing a coaching mission assigned to a father for a specific child.
 * Maps to the "mission" table (V2 migration).
 *
 * State machine transitions:
 * <pre>
 *   ASSIGNED → ACCEPTED (Father acknowledges)
 *   ASSIGNED → SKIPPED (Father explicitly declines)
 *   ASSIGNED → EXPIRED (Deadline passes without response)
 *   ACCEPTED → IN_PROGRESS (Father reports starting)
 *   ACCEPTED → EXPIRED (Deadline passes)
 *   IN_PROGRESS → COMPLETED (Father reports completion)
 *   IN_PROGRESS → ABANDONED (Deadline passes while in progress)
 *   COMPLETED → REFLECTED (Father provides post-mission reflection)
 * </pre>
 *
 * Expiration logic:
 * <ul>
 *   <li>Weekday assignment (Mon-Fri): expires_at = assigned_at + 24 hours</li>
 *   <li>Weekend assignment (Sat-Sun): expires_at = assigned_at + 48 hours</li>
 * </ul>
 */
@Entity
@Table(name = "mission")
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Column(name = "child_id", insertable = false, updatable = false)
    private Long childId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Column(name = "goal_id", insertable = false, updatable = false)
    private Long goalId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "category", length = 30, nullable = false)
    private String category;

    @Column(name = "difficulty", nullable = false)
    private int difficulty;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MissionStatus status = MissionStatus.ASSIGNED;

    @Column(name = "outcome_rating")
    private Integer outcomeRating;

    @Column(name = "outcome_notes", columnDefinition = "TEXT")
    private String outcomeNotes;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "reschedule_count", nullable = false)
    private int rescheduleCount = 0;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    @Column(name = "calendar_event_id", length = 255)
    private String calendarEventId;

    @Column(name = "reschedule_reason", length = 100)
    private String rescheduleReason;

    protected Mission() {
        // JPA requires a no-arg constructor
    }

    /**
     * Creates a new mission with expiration automatically calculated based on the day of week.
     *
     * @param father     the father receiving the mission
     * @param child      the child the mission targets
     * @param title      mission title (max 200 chars)
     * @param description mission description with action steps
     * @param category   mission category
     * @param difficulty difficulty level (1-5)
     * @param estimatedMinutes estimated time in minutes
     */
    public Mission(Father father, Child child, String title, String description,
                   String category, int difficulty, int estimatedMinutes) {
        this.father = father;
        this.child = child;
        this.title = title;
        this.description = description;
        this.category = category;
        this.difficulty = difficulty;
        this.estimatedMinutes = estimatedMinutes;
        this.assignedAt = Instant.now();
        this.expiresAt = calculateExpiration(this.assignedAt);
    }

    // ─── State Transition ────────────────────────────────────────────────

    /**
     * Transitions this mission to the given target status.
     *
     * @param target the desired new status
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public void transitionTo(MissionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Mission", id, status.name(), target.name());
        }
        this.status = target;

        // Set timestamps for specific transitions
        if (target == MissionStatus.ACCEPTED) {
            this.acceptedAt = Instant.now();
        } else if (target == MissionStatus.COMPLETED) {
            this.completedAt = Instant.now();
        }
    }

    /**
     * Checks whether this mission is in a terminal state (no further transitions possible).
     */
    public boolean isTerminal() {
        return status.getValidTransitions().isEmpty();
    }

    /**
     * Checks whether this mission is in an active (non-terminal) state.
     * Active states: ASSIGNED, ACCEPTED, IN_PROGRESS
     */
    public boolean isActive() {
        return status == MissionStatus.ASSIGNED
                || status == MissionStatus.ACCEPTED
                || status == MissionStatus.IN_PROGRESS;
    }

    /**
     * Checks whether this mission has expired (current time is past expires_at
     * and the mission is still in an expirable state).
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt) && isExpirable();
    }

    /**
     * Checks whether the mission is in a state where it can expire.
     */
    private boolean isExpirable() {
        return status == MissionStatus.ASSIGNED || status == MissionStatus.ACCEPTED;
    }

    // ─── Expiration Logic ────────────────────────────────────────────────

    /**
     * Calculates the expiration instant based on the assignment time.
     * Weekday assignment (Mon-Fri): +24 hours
     * Weekend assignment (Sat-Sun): +48 hours
     *
     * @param assignmentTime the time the mission was assigned
     * @return the computed expiration time
     */
    public static Instant calculateExpiration(Instant assignmentTime) {
        return calculateExpiration(assignmentTime, ZoneId.of("UTC"));
    }

    /**
     * Calculates the expiration instant based on the assignment time and timezone.
     * Weekday assignment (Mon-Fri): +24 hours
     * Weekend assignment (Sat-Sun): +48 hours
     *
     * @param assignmentTime the time the mission was assigned
     * @param zoneId         the timezone to use for day-of-week determination
     * @return the computed expiration time
     */
    public static Instant calculateExpiration(Instant assignmentTime, ZoneId zoneId) {
        ZonedDateTime zdt = assignmentTime.atZone(zoneId);
        DayOfWeek day = zdt.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return assignmentTime.plusSeconds(48 * 3600);
        } else {
            return assignmentTime.plusSeconds(24 * 3600);
        }
    }

    /**
     * Checks whether the given instant falls on a weekend day in the given timezone.
     *
     * @param instant the time to check
     * @param zoneId  the timezone for day-of-week determination
     * @return true if the day is Saturday or Sunday
     */
    public static boolean isWeekend(Instant instant, ZoneId zoneId) {
        DayOfWeek day = instant.atZone(zoneId).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Father getFather() {
        return father;
    }

    public void setFather(Father father) {
        this.father = father;
    }

    public Long getFatherId() {
        return fatherId;
    }

    public void setFatherId(Long fatherId) {
        this.fatherId = fatherId;
    }

    public Child getChild() {
        return child;
    }

    public void setChild(Child child) {
        this.child = child;
    }

    public Long getChildId() {
        return childId;
    }

    public void setChildId(Long childId) {
        this.childId = childId;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
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

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
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

    public int getRescheduleCount() {
        return rescheduleCount;
    }

    public void setRescheduleCount(int rescheduleCount) {
        this.rescheduleCount = rescheduleCount;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(Instant scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public Instant getReminderSentAt() {
        return reminderSentAt;
    }

    public void setReminderSentAt(Instant reminderSentAt) {
        this.reminderSentAt = reminderSentAt;
    }

    public Instant getLastRemindedAt() {
        return lastRemindedAt;
    }

    public void setLastRemindedAt(Instant lastRemindedAt) {
        this.lastRemindedAt = lastRemindedAt;
    }

    public String getCalendarEventId() {
        return calendarEventId;
    }

    public void setCalendarEventId(String calendarEventId) {
        this.calendarEventId = calendarEventId;
    }

    public String getRescheduleReason() {
        return rescheduleReason;
    }

    public void setRescheduleReason(String rescheduleReason) {
        this.rescheduleReason = rescheduleReason;
    }

    // ─── Scheduling Methods ──────────────────────────────────────────────

    /**
     * Reschedules this mission to a new time.
     * Increments the reschedule count and updates expiration.
     *
     * @param newScheduledFor the new scheduled time
     * @param reason          the reason for rescheduling (e.g., TOO_BUSY, CHILD_UNAVAILABLE)
     * @return true if rescheduling was allowed (under max limit), false otherwise
     */
    public boolean reschedule(Instant newScheduledFor, String reason) {
        if (rescheduleCount >= 3) {
            return false; // Max 3 reschedules allowed
        }
        this.rescheduleCount++;
        this.scheduledFor = newScheduledFor;
        this.rescheduleReason = reason;
        // Extend expiration to 24 hours after new scheduled time
        this.expiresAt = newScheduledFor.plusSeconds(24 * 3600);
        return true;
    }

    /**
     * Checks if this mission can be rescheduled (under the limit).
     */
    public boolean canReschedule() {
        return rescheduleCount < 3;
    }

    /**
     * Checks if a reminder should be sent for this mission.
     * A reminder should be sent if:
     * - Mission is in ASSIGNED or ACCEPTED status
     * - Either no reminder was sent, or 12+ hours have passed since last reminder
     */
    public boolean shouldSendReminder() {
        if (status != MissionStatus.ASSIGNED && status != MissionStatus.ACCEPTED) {
            return false;
        }
        if (lastRemindedAt == null) {
            return true;
        }
        // At least 12 hours between reminders
        return Instant.now().isAfter(lastRemindedAt.plusSeconds(12 * 3600));
    }

    /**
     * Marks that a reminder was sent for this mission.
     */
    public void markReminderSent() {
        Instant now = Instant.now();
        if (this.reminderSentAt == null) {
            this.reminderSentAt = now;
        }
        this.lastRemindedAt = now;
    }
}
