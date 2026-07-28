package com.dadcoach.api.child;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating or updating a child profile.
 * <p>
 * Validation rules (per SPEC-007 Requirement 7 criteria 3):
 * <ul>
 *   <li>name: 1-100 characters, non-empty</li>
 *   <li>birth_date: valid date between 0 and 18 years in the past</li>
 *   <li>interests: array of strings, each 1-100 characters, max 20 items</li>
 *   <li>challenges: array of strings, each 1-200 characters, max 10 items</li>
 * </ul>
 */
public class ChildCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    private String name;

    @NotNull(message = "Birth date is required")
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;

    @Size(max = 20, message = "Maximum 20 interests allowed")
    private List<@NotBlank(message = "Interest must not be blank") @Size(min = 1, max = 100, message = "Each interest must be between 1 and 100 characters") String> interests;

    @Size(max = 10, message = "Maximum 10 challenges allowed")
    private List<@NotBlank(message = "Challenge must not be blank") @Size(min = 1, max = 200, message = "Each challenge must be between 1 and 200 characters") String> challenges;

    public ChildCreateRequest() {
    }

    public ChildCreateRequest(String name, LocalDate birthDate, List<String> interests, List<String> challenges) {
        this.name = name;
        this.birthDate = birthDate;
        this.interests = interests;
        this.challenges = challenges;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public List<String> getChallenges() {
        return challenges;
    }

    public void setChallenges(List<String> challenges) {
        this.challenges = challenges;
    }

    /**
     * Validates that the birth date is within the 0-18 year range.
     * <p>
     * A child must be between 0 and 18 years old (per SPEC-002 Requirement 2 criteria 4).
     * This means birth_date must be at most 18 years in the past and not in the future.
     *
     * @return true if birth date is valid (0-18 years ago), false otherwise
     */
    public boolean isBirthDateInValidRange() {
        if (birthDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate eighteenYearsAgo = today.minusYears(18);
        // Birth date must not be in the future and must not be more than 18 years ago
        return !birthDate.isAfter(today) && !birthDate.isBefore(eighteenYearsAgo);
    }
}
