package com.dadcoach.domain.child;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Service layer for Child entity operations: create, update, archive, and retrieval.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Maximum of 8 active children per Father (Requirement 2.2)</li>
 *   <li>Birth date must be between 0 and 18 years in the past (Requirement 2.4)</li>
 *   <li>Archive sets status to ARCHIVED (Requirement 2.5)</li>
 *   <li>Interests and challenges can be updated at any time (Requirement 2.8)</li>
 * </ul>
 */
@Service
@Transactional
public class ChildService {

    private static final int MAX_CHILDREN_PER_FATHER = 8;
    private static final int MAX_CHILD_AGE_YEARS = 18;

    private final ChildRepository childRepository;
    private final FatherRepository fatherRepository;

    public ChildService(ChildRepository childRepository, FatherRepository fatherRepository) {
        this.childRepository = childRepository;
        this.fatherRepository = fatherRepository;
    }

    // ─── Create ──────────────────────────────────────────────────────────

    /**
     * Creates a new Child linked to the specified Father.
     *
     * @param fatherId  the ID of the Father
     * @param name      the child's name
     * @param birthDate the child's birth date
     * @return the persisted Child entity
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     * @throws BusinessRuleViolationException if the Father already has 8 active children
     * @throws BusinessRuleViolationException if the birth date is not between 0 and 18 years in the past
     */
    public Child createChild(Long fatherId, String name, LocalDate birthDate) {
        return createChild(fatherId, name, birthDate, null, null, null);
    }

    /**
     * Creates a new Child linked to the specified Father with optional fields.
     *
     * @param fatherId   the ID of the Father
     * @param name       the child's name
     * @param birthDate  the child's birth date
     * @param gender     the child's gender (nullable)
     * @param interests  the child's interests (nullable)
     * @param challenges the child's challenges (nullable)
     * @return the persisted Child entity
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     * @throws BusinessRuleViolationException if the Father already has 8 active children
     * @throws BusinessRuleViolationException if the birth date is not between 0 and 18 years in the past
     */
    public Child createChild(Long fatherId, String name, LocalDate birthDate,
                             String gender, List<String> interests, List<String> challenges) {
        // Validate father exists
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        // Validate max children limit
        long activeChildCount = childRepository.countActiveByFatherId(fatherId);
        if (activeChildCount >= MAX_CHILDREN_PER_FATHER) {
            throw new BusinessRuleViolationException(
                    "MAX_CHILDREN_EXCEEDED",
                    "Father already has " + activeChildCount + " active children. Maximum allowed is " + MAX_CHILDREN_PER_FATHER
            );
        }

        // Validate birth date is between 0 and 18 years in the past
        validateBirthDate(birthDate);

        // Create the child entity
        Child child = new Child(father, name, birthDate);
        if (gender != null) {
            child.setGender(gender);
        }
        if (interests != null) {
            child.setInterests(interests);
        }
        if (challenges != null) {
            child.setChallenges(challenges);
        }

        return childRepository.save(child);
    }

    // ─── Update ──────────────────────────────────────────────────────────

    /**
     * Updates modifiable fields on a Child entity.
     * Only non-null parameters are applied.
     *
     * @param childId    the ID of the Child to update
     * @param name       the new name (nullable - only updated if non-null)
     * @param interests  the new interests list (nullable - only updated if non-null)
     * @param challenges the new challenges list (nullable - only updated if non-null)
     * @return the updated Child entity
     * @throws ResourceNotFoundException if no Child exists with the given ID
     */
    public Child updateChild(Long childId, String name, List<String> interests, List<String> challenges) {
        Child child = getChild(childId);

        if (name != null) {
            child.setName(name);
        }
        if (interests != null) {
            child.setInterests(interests);
        }
        if (challenges != null) {
            child.setChallenges(challenges);
        }
        child.setUpdatedAt(Instant.now());

        return childRepository.save(child);
    }

    // ─── Archive ─────────────────────────────────────────────────────────

    /**
     * Archives a Child by setting status to ARCHIVED.
     * Archived children are excluded from future mission generation.
     *
     * @param childId the ID of the Child to archive
     * @return the updated Child entity
     * @throws ResourceNotFoundException      if no Child exists with the given ID
     * @throws BusinessRuleViolationException if the child is already archived
     */
    public Child archiveChild(Long childId) {
        Child child = getChild(childId);

        if ("ARCHIVED".equals(child.getStatus())) {
            throw new BusinessRuleViolationException(
                    "CHILD_ALREADY_ARCHIVED",
                    "Child with ID " + childId + " is already archived"
            );
        }

        child.setStatus("ARCHIVED");
        child.setUpdatedAt(Instant.now());

        return childRepository.save(child);
    }

    // ─── Retrieval ───────────────────────────────────────────────────────

    /**
     * Gets a Child by ID.
     *
     * @param childId the Child ID
     * @return the Child entity
     * @throws ResourceNotFoundException if no Child exists with the given ID
     */
    @Transactional(readOnly = true)
    public Child getChild(Long childId) {
        return childRepository.findById(childId)
                .orElseThrow(() -> new ResourceNotFoundException("Child", childId));
    }

    /**
     * Retrieves all children for a given Father.
     *
     * @param fatherId the Father ID
     * @return list of all children (including archived) for the Father
     */
    @Transactional(readOnly = true)
    public List<Child> getChildrenByFather(Long fatherId) {
        return childRepository.findByFatherId(fatherId);
    }

    // ─── Validation ──────────────────────────────────────────────────────

    /**
     * Validates that the birth date is between 0 and 18 years in the past (inclusive).
     * The birth date must not be in the future and must not be more than 18 years ago.
     *
     * @param birthDate the birth date to validate
     * @throws BusinessRuleViolationException if the birth date is out of range
     */
    void validateBirthDate(LocalDate birthDate) {
        LocalDate today = LocalDate.now();

        if (birthDate.isAfter(today)) {
            throw new BusinessRuleViolationException(
                    "INVALID_BIRTH_DATE",
                    "Birth date cannot be in the future"
            );
        }

        int yearsOld = Period.between(birthDate, today).getYears();
        if (yearsOld > MAX_CHILD_AGE_YEARS) {
            throw new BusinessRuleViolationException(
                    "INVALID_BIRTH_DATE",
                    "Child must be 18 years old or younger. Computed age: " + yearsOld
            );
        }
    }
}
