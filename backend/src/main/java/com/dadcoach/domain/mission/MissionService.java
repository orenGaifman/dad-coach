package com.dadcoach.domain.mission;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.goal.Goal;
import com.dadcoach.domain.goal.GoalRepository;
import com.dadcoach.mission.LegacyMissionStatus;
import com.dadcoach.statemachine.StateMachineEngine;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Mission entity lifecycle management.
 *
 * <p>Handles mission creation, state transitions via the StateMachineEngine (with audit logging),
 * and mission retrieval. All state transitions are validated by the Mission entity's built-in
 * state machine and additionally logged through the centralized StateMachineEngine.</p>
 *
 * <p>State machine:
 * <pre>
 *   ASSIGNED → ACCEPTED → IN_PROGRESS → COMPLETED → REFLECTED
 *   ASSIGNED → SKIPPED
 *   ASSIGNED/ACCEPTED → EXPIRED
 *   IN_PROGRESS → ABANDONED
 * </pre>
 */
@Service
@Transactional
public class MissionService {

    private final MissionRepository missionRepository;
    private final FatherRepository fatherRepository;
    private final ChildRepository childRepository;
    private final GoalRepository goalRepository;
    private final StateMachineEngine stateMachineEngine;

    public MissionService(MissionRepository missionRepository,
                          FatherRepository fatherRepository,
                          ChildRepository childRepository,
                          GoalRepository goalRepository,
                          StateMachineEngine stateMachineEngine) {
        this.missionRepository = missionRepository;
        this.fatherRepository = fatherRepository;
        this.childRepository = childRepository;
        this.goalRepository = goalRepository;
        this.stateMachineEngine = stateMachineEngine;
    }

    // ─── Creation ────────────────────────────────────────────────────────

    /**
     * Creates a new mission with ASSIGNED status.
     *
     * <p>Enforces the single-active-mission-per-child constraint: if the child already has
     * an active mission (ASSIGNED, ACCEPTED, or IN_PROGRESS), creation is rejected.</p>
     *
     * @param fatherId         the ID of the father receiving the mission
     * @param childId          the ID of the child the mission targets
     * @param goalId           the ID of the related goal (nullable)
     * @param title            the mission title (max 200 chars)
     * @param description      the mission description with action steps
     * @param category         the mission category
     * @param difficulty       difficulty level (1-5)
     * @param estimatedMinutes estimated time in minutes
     * @return the persisted Mission entity
     * @throws ResourceNotFoundException      if the father or child is not found
     * @throws BusinessRuleViolationException if the child already has an active mission
     */
    public Mission createMission(Long fatherId, Long childId, Long goalId,
                                 String title, String description,
                                 String category, int difficulty, int estimatedMinutes) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));

        // Enforce single-active-mission-per-child constraint
        long activeCount = missionRepository.countActiveMissionsByChildId(childId);
        if (activeCount > 0) {
            throw new BusinessRuleViolationException("SINGLE_ACTIVE_MISSION_PER_CHILD",
                    "Child " + childId + " already has an active mission");
        }

        Mission mission = new Mission(father, child, title, description, category, difficulty, estimatedMinutes);

        // Link to goal if provided
        if (goalId != null) {
            Goal goal = goalRepository.findById(goalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
            mission.setGoal(goal);
        }

        return missionRepository.save(mission);
    }

    // ─── State Transitions ───────────────────────────────────────────────

    /**
     * Accepts a mission (ASSIGNED → ACCEPTED).
     *
     * @param missionId the mission ID
     * @return the updated Mission entity
     * @throws ResourceNotFoundException                          if the mission is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Mission acceptMission(Long missionId) {
        return transitionMission(missionId, LegacyMissionStatus.ACCEPTED, "Father acknowledged mission");
    }

    /**
     * Starts a mission (ACCEPTED → IN_PROGRESS).
     *
     * @param missionId the mission ID
     * @return the updated Mission entity
     * @throws ResourceNotFoundException                          if the mission is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Mission startMission(Long missionId) {
        return transitionMission(missionId, LegacyMissionStatus.IN_PROGRESS, "Father reported starting");
    }

    /**
     * Completes a mission (IN_PROGRESS → COMPLETED) and sets outcome rating and notes.
     *
     * @param missionId     the mission ID
     * @param outcomeRating the outcome rating (1-5)
     * @param outcomeNotes  optional notes about the outcome
     * @return the updated Mission entity
     * @throws ResourceNotFoundException                          if the mission is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     * @throws BusinessRuleViolationException                     if outcomeRating is out of range
     */
    public Mission completeMission(Long missionId, int outcomeRating, String outcomeNotes) {
        if (outcomeRating < 1 || outcomeRating > 5) {
            throw new BusinessRuleViolationException("INVALID_OUTCOME_RATING",
                    "Outcome rating must be between 1 and 5, got: " + outcomeRating);
        }

        Mission mission = findMissionOrThrow(missionId);

        // Perform audited transition
        stateMachineEngine.transition(
                "Mission", mission.getId(), mission.getStatus(), LegacyMissionStatus.COMPLETED,
                "Father reported completion"
        );
        mission.transitionTo(LegacyMissionStatus.COMPLETED);

        // Set outcome data
        mission.setOutcomeRating(outcomeRating);
        mission.setOutcomeNotes(outcomeNotes);

        return missionRepository.save(mission);
    }

    /**
     * Skips a mission (ASSIGNED → SKIPPED).
     *
     * @param missionId the mission ID
     * @return the updated Mission entity
     * @throws ResourceNotFoundException                          if the mission is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Mission skipMission(Long missionId) {
        return transitionMission(missionId, LegacyMissionStatus.SKIPPED, "Father explicitly declined");
    }

    /**
     * Expires a mission (ASSIGNED/ACCEPTED → EXPIRED).
     *
     * @param missionId the mission ID
     * @return the updated Mission entity
     * @throws ResourceNotFoundException                          if the mission is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Mission expireMission(Long missionId) {
        return transitionMission(missionId, LegacyMissionStatus.EXPIRED, "Deadline passed without response");
    }

    /**
     * Abandons a mission (IN_PROGRESS → ABANDONED).
     *
     * @param missionId the mission ID
     * @return the updated Mission entity
     * @throws ResourceNotFoundException                          if the mission is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Mission abandonMission(Long missionId) {
        return transitionMission(missionId, LegacyMissionStatus.ABANDONED, "Deadline passed while in progress");
    }

    /**
     * Records a reflection on a completed mission (COMPLETED → REFLECTED).
     *
     * @param missionId the mission ID
     * @return the updated Mission entity
     * @throws ResourceNotFoundException                          if the mission is not found
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Mission reflectOnMission(Long missionId) {
        return transitionMission(missionId, LegacyMissionStatus.REFLECTED, "Father provided post-mission reflection");
    }

    /**
     * Reschedules a mission to a new time.
     * Only allowed if the mission hasn't been rescheduled 3 times already.
     *
     * @param missionId      the mission ID
     * @param scheduledFor   the new scheduled time
     * @param reason         the reason for rescheduling (e.g., TOO_BUSY, CHILD_UNAVAILABLE)
     * @return the updated Mission entity
     * @throws ResourceNotFoundException      if the mission is not found
     * @throws BusinessRuleViolationException if max reschedules exceeded
     */
    public Mission rescheduleMission(Long missionId, java.time.Instant scheduledFor, String reason) {
        Mission mission = findMissionOrThrow(missionId);
        
        if (!mission.canReschedule()) {
            throw new BusinessRuleViolationException("MAX_RESCHEDULES_EXCEEDED",
                    "Mission " + missionId + " has been rescheduled " + mission.getRescheduleCount() + 
                    " times. Maximum is 3.");
        }
        
        mission.reschedule(scheduledFor, reason);
        return missionRepository.save(mission);
    }

    /**
     * Updates the scheduled time for a mission.
     * Used when the father specifies when they will do the mission.
     *
     * @param missionId    the mission ID
     * @param scheduledFor the scheduled time
     * @return the updated Mission entity
     * @throws ResourceNotFoundException if the mission is not found
     */
    public Mission setMissionSchedule(Long missionId, java.time.Instant scheduledFor) {
        Mission mission = findMissionOrThrow(missionId);
        mission.setScheduledFor(scheduledFor);
        // Update expiration to 24 hours after scheduled time
        mission.setExpiresAt(scheduledFor.plusSeconds(24 * 3600));
        return missionRepository.save(mission);
    }

    /**
     * Gets all missions for a father that need reminders.
     *
     * @param fatherId the father ID
     * @return list of missions needing reminders
     */
    @Transactional(readOnly = true)
    public List<Mission> getMissionsPendingReminder(Long fatherId) {
        return missionRepository.findActiveMissionsByFatherId(fatherId);
    }

    // ─── Retrieval ───────────────────────────────────────────────────────

    /**
     * Gets a mission by ID.
     *
     * @param missionId the mission ID
     * @return the Mission entity
     * @throws ResourceNotFoundException if the mission is not found
     */
    @Transactional(readOnly = true)
    public Mission getMission(Long missionId) {
        return findMissionOrThrow(missionId);
    }

    /**
     * Gets all active missions for a child (status ASSIGNED, ACCEPTED, or IN_PROGRESS).
     *
     * @param childId the child ID
     * @return list of active missions for the child
     */
    @Transactional(readOnly = true)
    public List<Mission> getActiveMissionsForChild(Long childId) {
        return missionRepository.findActiveMissionsByChildId(childId);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────

    /**
     * Performs an audited state transition on a mission.
     * Uses the StateMachineEngine for audit logging and the Mission entity for state validation.
     */
    private Mission transitionMission(Long missionId, LegacyMissionStatus targetStatus, String reason) {
        Mission mission = findMissionOrThrow(missionId);

        // Audit-logged transition via StateMachineEngine
        stateMachineEngine.transition(
                "Mission", mission.getId(), mission.getStatus(), targetStatus, reason
        );

        // Apply on the entity (this also sets timestamps like acceptedAt, completedAt)
        mission.transitionTo(targetStatus);

        return missionRepository.save(mission);
    }

    private Mission findMissionOrThrow(Long missionId) {
        return missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", missionId));
    }
}
