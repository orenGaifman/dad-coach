package com.dadcoach.domain.flash;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Template for flash (quick spontaneous) missions.
 * These are 2-5 minute activities that can be done anytime.
 */
@Entity
@Table(name = "flash_mission_template")
public class FlashMissionTemplate {

    public enum Context { HOME, CAR, OUTDOOR, ANYWHERE }
    public enum Category { CONNECTION, PLAY, TALK, PHYSICAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title_he", length = 200, nullable = false)
    private String titleHe;

    @Column(name = "title_en", length = 200, nullable = false)
    private String titleEn;

    @Column(name = "description_he", columnDefinition = "TEXT", nullable = false)
    private String descriptionHe;

    @Column(name = "description_en", columnDefinition = "TEXT", nullable = false)
    private String descriptionEn;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "context", length = 30)
    private Context context;

    @Column(name = "estimated_minutes", nullable = false)
    private Integer estimatedMinutes = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private Category category = Category.CONNECTION;

    @Column(name = "difficulty", nullable = false)
    private Integer difficulty = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FlashMissionTemplate() {}

    // ─── Localized Getters ───────────────────────────────────────────────

    /**
     * Gets the title in the specified locale.
     */
    public String getTitle(String locale) {
        return "he".equals(locale) ? titleHe : titleEn;
    }

    /**
     * Gets the description in the specified locale.
     */
    public String getDescription(String locale) {
        return "he".equals(locale) ? descriptionHe : descriptionEn;
    }

    /**
     * Checks if this template is suitable for a child of the given age.
     */
    public boolean isSuitableForAge(int childAge) {
        if (minAge != null && childAge < minAge) return false;
        if (maxAge != null && childAge > maxAge) return false;
        return true;
    }

    // ─── Getters & Setters ───────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitleHe() { return titleHe; }
    public void setTitleHe(String titleHe) { this.titleHe = titleHe; }

    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }

    public String getDescriptionHe() { return descriptionHe; }
    public void setDescriptionHe(String descriptionHe) { this.descriptionHe = descriptionHe; }

    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }

    public Integer getMinAge() { return minAge; }
    public void setMinAge(Integer minAge) { this.minAge = minAge; }

    public Integer getMaxAge() { return maxAge; }
    public void setMaxAge(Integer maxAge) { this.maxAge = maxAge; }

    public Context getContext() { return context; }
    public void setContext(Context context) { this.context = context; }

    public Integer getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(Integer estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
