package com.dadcoach.domain.father;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.PhoneValidator;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.statemachine.StateMachineEngine;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Service layer for Father entity CRUD operations, status transitions, and pause/resume logic.
 */
@Service
@Transactional
public class FatherService {

    private static final int MAX_PAUSE_DAYS = 30;

    private final FatherRepository fatherRepository;
    private final StateMachineEngine stateMachineEngine;

    public FatherService(FatherRepository fatherRepository, StateMachineEngine stateMachineEngine) {
        this.fatherRepository = fatherRepository;
        this.stateMachineEngine = stateMachineEngine;
    }

    // ─── CRUD Operations ─────────────────────────────────────────────────

    /**
     * Creates a new Father with NOT_STARTED status.
     *
     * @param phone the phone number in E.164 format
     * @return the persisted Father entity
     * @throws BusinessRuleViolationException if the phone number is not valid E.164 format
     */
    public Father createFather(String phone) {
        PhoneValidator.requireValidE164(phone);
        Father father = new Father(phone);
        return fatherRepository.save(father);
    }

    /**
     * Gets a Father by ID.
     *
     * @param id the Father ID
     * @return the Father entity
     * @throws ResourceNotFoundException if no Father exists with the given ID
     */
    @Transactional(readOnly = true)
    public Father getFather(Long id) {
        return fatherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Father", id));
    }

    /**
     * Gets a Father by phone number.
     *
     * @param phone the phone number
     * @return the Father entity
     * @throws ResourceNotFoundException if no Father exists with the given phone
     */
    @Transactional(readOnly = true)
    public Father getByPhone(String phone) {
        return fatherRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("Father", phone));
    }

    /**
     * Updates profile fields for a Father.
     *
     * @param id          the Father ID
     * @param displayName the display name (nullable - only updated if non-null)
     * @param timezone    the timezone (nullable - only updated if non-null)
     * @param locale      the locale (nullable - only updated if non-null)
     * @return the updated Father entity
     * @throws ResourceNotFoundException if no Father exists with the given ID
     */
    public Father updateProfile(Long id, String displayName, String timezone, String locale) {
        Father father = getFather(id);
        if (displayName != null) {
            father.setDisplayName(displayName);
        }
        if (timezone != null) {
            father.setTimezone(timezone);
        }
        if (locale != null) {
            father.setLocale(locale);
        }
        return fatherRepository.save(father);
    }

    // ─── Status Transitions ──────────────────────────────────────────────

    /**
     * Validates and applies a status transition using the StateMachineEngine.
     *
     * @param id           the Father ID
     * @param targetStatus the desired target status
     * @param reason       the reason for the transition
     * @return the updated Father entity
     * @throws ResourceNotFoundException             if no Father exists with the given ID
     * @throws com.dadcoach.common.InvalidStateTransitionException if the transition is invalid
     */
    public Father transitionStatus(Long id, FatherStatus targetStatus, String reason) {
        Father father = getFather(id);
        FatherStatus newStatus = stateMachineEngine.transition(
                "Father", father.getId(), father.getStatus(), targetStatus, reason
        );
        father.setStatus(newStatus);
        return fatherRepository.save(father);
    }

    /**
     * Convenience method to activate a Father (ONBOARDING → ACTIVE).
     * Sets the activation date to today.
     *
     * @param id the Father ID
     * @return the updated Father entity
     */
    public Father activateFather(Long id) {
        Father father = transitionStatus(id, FatherStatus.ACTIVE, "Onboarding completed");
        father.setActivationDate(LocalDate.now());
        return fatherRepository.save(father);
    }

    // ─── Pause/Resume Logic ──────────────────────────────────────────────

    /**
     * Pauses a Father for the requested number of days, capped at 30 days.
     * Transitions status from ACTIVE to PAUSED and sets pauseUntil.
     *
     * @param id            the Father ID
     * @param requestedDays the requested pause duration in days
     * @return the updated Father entity
     */
    public Father pauseFather(Long id, int requestedDays) {
        Father father = transitionStatus(id, FatherStatus.PAUSED, "Father requested pause");
        int effectiveDays = Math.min(requestedDays, MAX_PAUSE_DAYS);
        father.setPauseUntil(LocalDate.now().plusDays(effectiveDays));
        return fatherRepository.save(father);
    }

    /**
     * Resumes a paused Father. Transitions from PAUSED to ACTIVE and clears pauseUntil.
     *
     * @param id the Father ID
     * @return the updated Father entity
     */
    public Father resumeFather(Long id) {
        Father father = transitionStatus(id, FatherStatus.ACTIVE, "Father resumed or pause expired");
        father.setPauseUntil(null);
        return fatherRepository.save(father);
    }

    // ─── Interaction Tracking ────────────────────────────────────────────

    /**
     * Records a new interaction by updating lastInteractionAt to the current time.
     *
     * @param id the Father ID
     * @return the updated Father entity
     */
    public Father recordInteraction(Long id) {
        Father father = getFather(id);
        father.setLastInteractionAt(Instant.now());
        return fatherRepository.save(father);
    }

    // ─── Validation ──────────────────────────────────────────────────────

    /**
     * Updates the phone number for a Father, validating E.164 format.
     *
     * @param id    the Father ID
     * @param phone the new phone number in E.164 format
     * @return the updated Father entity
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     * @throws BusinessRuleViolationException if the phone number is not valid E.164 format
     */
    public Father updatePhone(Long id, String phone) {
        PhoneValidator.requireValidE164(phone);
        Father father = getFather(id);
        father.setPhone(phone);
        return fatherRepository.save(father);
    }
}
