package com.dadcoach.api.child;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for child profile data.
 * <p>
 * Age is computed dynamically from birth_date — never stored
 * (per SPEC-002 Requirement 2 criteria 3).
 * <p>
 * This DTO never exposes internal fields (embeddings, AI data, internal flags).
 */
public class ChildResponseDto {

    private UUID id;
    private UUID fatherId;
    private String name;
    private LocalDate birthDate;
    private int age;
    private List<String> interests;
    private List<String> challenges;
    private String status;
    private String createdAt;
    private String updatedAt;

    public ChildResponseDto() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFatherId() {
        return fatherId;
    }

    public void setFatherId(UUID fatherId) {
        this.fatherId = fatherId;
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

    /**
     * Dynamically computed age based on birth_date.
     * Never stored as a field in the database.
     */
    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
