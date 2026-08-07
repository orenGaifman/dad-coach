package com.dadcoach.domain.father;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.PhoneValidator;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.statemachine.StateMachineEngine;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Service layer for Father entity CRUD operations and status transitions.
 */
@Service
@Transactional
public class FatherService {

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
     * Convenience method to activate a Father (ONBOARDING → ACTIVE).
     * Sets the activation date to today.
     *
     * @param id the Father ID
     * @return the updated Father entity
     */
    public Father activateFather(Long id) {
        Father father = getFather(id);
        FatherStatus newStatus = stateMachineEngine.transition(
                "Father", father.getId(), father.getStatus(), FatherStatus.ACTIVE, "Onboarding completed"
        );
        father.setStatus(newStatus);
        father.setActivationDate(LocalDate.now());
        return fatherRepository.save(father);
    }
}
