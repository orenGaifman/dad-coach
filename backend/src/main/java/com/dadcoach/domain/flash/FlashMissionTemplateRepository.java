package com.dadcoach.domain.flash;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for flash mission templates.
 */
@Repository
public interface FlashMissionTemplateRepository extends JpaRepository<FlashMissionTemplate, Long> {

    /**
     * Find all active templates.
     */
    List<FlashMissionTemplate> findByIsActiveTrue();

    /**
     * Find active templates suitable for a child's age.
     */
    @Query("SELECT t FROM FlashMissionTemplate t WHERE t.isActive = true " +
           "AND (t.minAge IS NULL OR t.minAge <= :age) " +
           "AND (t.maxAge IS NULL OR t.maxAge >= :age)")
    List<FlashMissionTemplate> findSuitableForAge(@Param("age") int age);

    /**
     * Find active templates for a specific context and age.
     */
    @Query("SELECT t FROM FlashMissionTemplate t WHERE t.isActive = true " +
           "AND (t.context = :context OR t.context = 'ANYWHERE') " +
           "AND (t.minAge IS NULL OR t.minAge <= :age) " +
           "AND (t.maxAge IS NULL OR t.maxAge >= :age)")
    List<FlashMissionTemplate> findByContextAndAge(@Param("context") FlashMissionTemplate.Context context,
                                                    @Param("age") int age);

    /**
     * Find active templates by category.
     */
    List<FlashMissionTemplate> findByIsActiveTrueAndCategory(FlashMissionTemplate.Category category);

    /**
     * Find templates that can be done anywhere (for quick suggestions).
     */
    @Query("SELECT t FROM FlashMissionTemplate t WHERE t.isActive = true " +
           "AND t.context = 'ANYWHERE' " +
           "AND (t.minAge IS NULL OR t.minAge <= :age) " +
           "AND (t.maxAge IS NULL OR t.maxAge >= :age) " +
           "ORDER BY t.difficulty ASC")
    List<FlashMissionTemplate> findAnywhereForAge(@Param("age") int age);
}
