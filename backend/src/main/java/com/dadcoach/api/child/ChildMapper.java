package com.dadcoach.api.child;

import java.time.LocalDate;
import java.time.Period;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Maps between domain Child entities and API response DTOs.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Computes age dynamically from birth_date (never stored)</li>
 *   <li>Filters sensitive/internal fields from responses</li>
 *   <li>Converts timestamps to ISO 8601 string format</li>
 * </ul>
 * <p>
 * Note: Since the domain Child entity is defined in SPEC-002 and may not yet
 * exist as a concrete class, this mapper works with a generic approach that
 * can be adapted when the domain entity is available.
 */
public class ChildMapper {

    /**
     * Computes the age in years from the given birth date.
     * Age is always computed dynamically — never stored (per SPEC-002 Req 2 criteria 3).
     *
     * @param birthDate the child's date of birth
     * @return age in completed years
     */
    public static int computeAge(LocalDate birthDate) {
        if (birthDate == null) {
            return 0;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * Builds a ChildResponseDto from raw field values.
     * Used when the domain entity representation is available.
     *
     * @param id         child ID
     * @param fatherId   owning father ID
     * @param name       child name
     * @param birthDate  child birth date
     * @param interests  list of interests
     * @param challenges list of challenges
     * @param status     current status (ACTIVE, ARCHIVED)
     * @param createdAt  creation timestamp
     * @param updatedAt  last update timestamp
     * @return populated ChildResponseDto
     */
    public static ChildResponseDto toDto(UUID id, UUID fatherId, String name,
                                         LocalDate birthDate, List<String> interests,
                                         List<String> challenges, String status,
                                         Instant createdAt, Instant updatedAt) {
        ChildResponseDto dto = new ChildResponseDto();
        dto.setId(id);
        dto.setFatherId(fatherId);
        dto.setName(name);
        dto.setBirthDate(birthDate);
        dto.setAge(computeAge(birthDate));
        dto.setInterests(interests);
        dto.setChallenges(challenges);
        dto.setStatus(status);
        dto.setCreatedAt(createdAt != null ? createdAt.toString() : null);
        dto.setUpdatedAt(updatedAt != null ? updatedAt.toString() : null);
        return dto;
    }
}
