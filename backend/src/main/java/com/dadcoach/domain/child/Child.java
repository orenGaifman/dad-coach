package com.dadcoach.domain.child;

import com.dadcoach.domain.father.Father;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a child in the coaching system.
 * Maps to the "child" table (V3 migration).
 */
@Entity
@Table(name = "child")
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id", nullable = false)
    private Father father;

    @Column(name = "father_id", insertable = false, updatable = false)
    private Long fatherId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "gender", length = 10)
    private String gender;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "interests", columnDefinition = "text[]")
    private List<String> interests = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "challenges", columnDefinition = "text[]")
    private List<String> challenges = new ArrayList<>();

    @Column(name = "relationship_quality")
    private int relationshipQuality = 3;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Child() {
        // JPA requires a no-arg constructor
    }

    public Child(Father father, String name, LocalDate birthDate) {
        this.father = father;
        this.name = name;
        this.birthDate = birthDate;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ─── Computed Methods ────────────────────────────────────────────────

    /**
     * Computes the child's current age in whole years (floor).
     *
     * @return the age in years
     */
    public int getAge() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * Checks if the child's birthday (month-day) falls within the given number
     * of days from today, including year wrap-around (e.g., Dec 30 checking 7 days
     * ahead will include Jan dates).
     *
     * @param days the lookahead window in days
     * @return true if the birthday is within the window
     */
    public boolean isBirthdayWithin(int days) {
        LocalDate today = LocalDate.now();
        MonthDay birthday = MonthDay.from(birthDate);

        for (int i = 0; i <= days; i++) {
            LocalDate checkDate = today.plusDays(i);
            if (MonthDay.from(checkDate).equals(birthday)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the developmental bracket for this child based on current age.
     *
     * @return the developmental bracket
     */
    public DevelopmentalBracket getDevelopmentalBracket() {
        return DevelopmentalBracket.fromAge(getAge());
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

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
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

    public int getRelationshipQuality() {
        return relationshipQuality;
    }

    public void setRelationshipQuality(int relationshipQuality) {
        this.relationshipQuality = relationshipQuality;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
