package com.dadcoach.domain.reflection;

import com.dadcoach.common.BusinessRuleViolationException;
import com.dadcoach.common.ResourceNotFoundException;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.mission.Mission;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

/**
 * Service layer for Reflection entity operations.
 *
 * <p>Business rules:</p>
 * <ul>
 *   <li>At most 1 reflection per father per calendar day in their timezone (Property 35)</li>
 *   <li>Reflection types: MISSION, WEEKLY, PHASE</li>
 * </ul>
 */
@Service
@Transactional
public class ReflectionService {

    /** Maximum number of reflections allowed per father per calendar day. */
    public static final int MAX_REFLECTIONS_PER_DAY = 1;

    private final ReflectionRepository reflectionRepository;
    private final FatherRepository fatherRepository;

    public ReflectionService(ReflectionRepository reflectionRepository, FatherRepository fatherRepository) {
        this.reflectionRepository = reflectionRepository;
        this.fatherRepository = fatherRepository;
    }

    /**
     * Creates a new reflection for a father, enforcing the daily limit.
     *
     * @param fatherId the father ID
     * @param type     the type of reflection
     * @return the created Reflection entity
     * @throws BusinessRuleViolationException if the daily reflection limit is exceeded
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     */
    public Reflection createReflection(Long fatherId, ReflectionType type) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        enforceMaxOnePerDay(father);

        Reflection reflection = new Reflection(father, type);
        return reflectionRepository.save(reflection);
    }

    /**
     * Creates a mission-related reflection for a father, enforcing the daily limit.
     *
     * @param fatherId the father ID
     * @param type     the type of reflection
     * @param mission  the mission being reflected upon
     * @return the created Reflection entity
     * @throws BusinessRuleViolationException if the daily reflection limit is exceeded
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     */
    public Reflection createReflection(Long fatherId, ReflectionType type, Mission mission) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        enforceMaxOnePerDay(father);

        Reflection reflection = new Reflection(father, type, mission);
        return reflectionRepository.save(reflection);
    }

    /**
     * Creates a reflection with a specific createdAt time (for testing and scheduling).
     *
     * @param fatherId  the father ID
     * @param type      the type of reflection
     * @param createdAt the creation timestamp
     * @return the created Reflection entity
     * @throws BusinessRuleViolationException if the daily reflection limit is exceeded
     * @throws ResourceNotFoundException      if no Father exists with the given ID
     */
    public Reflection createReflectionAt(Long fatherId, ReflectionType type, Instant createdAt) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));

        enforceMaxOnePerDayAt(father, createdAt);

        Reflection reflection = new Reflection(father, type);
        reflection.setCreatedAt(createdAt);
        return reflectionRepository.save(reflection);
    }

    /**
     * Checks whether a father can create a reflection today (in their timezone).
     *
     * @param fatherId the father ID
     * @return true if a reflection can be created (limit not reached)
     */
    @Transactional(readOnly = true)
    public boolean canCreateReflectionToday(Long fatherId) {
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("Father", fatherId));
        return getReflectionCountForToday(father) < MAX_REFLECTIONS_PER_DAY;
    }

    /**
     * Gets all reflections for a father.
     *
     * @param fatherId the father ID
     * @return list of reflections ordered by most recent first
     */
    @Transactional(readOnly = true)
    public List<Reflection> getReflections(Long fatherId) {
        return reflectionRepository.findByFatherIdOrderByCreatedAtDesc(fatherId);
    }

    // ─── Internal helpers ────────────────────────────────────────────────

    private void enforceMaxOnePerDay(Father father) {
        enforceMaxOnePerDayAt(father, Instant.now());
    }

    private void enforceMaxOnePerDayAt(Father father, Instant at) {
        int count = getReflectionCountForDay(father, at);
        if (count >= MAX_REFLECTIONS_PER_DAY) {
            throw new BusinessRuleViolationException(
                    "DAILY_REFLECTION_LIMIT_EXCEEDED",
                    "Father " + father.getId() + " has already completed " + MAX_REFLECTIONS_PER_DAY
                            + " reflection(s) today"
            );
        }
    }

    private int getReflectionCountForToday(Father father) {
        return getReflectionCountForDay(father, Instant.now());
    }

    /**
     * Counts reflections for a father on the calendar day containing the given instant,
     * in the father's configured timezone.
     */
    int getReflectionCountForDay(Father father, Instant at) {
        ZoneId zone = ZoneId.of(father.getTimezone() != null ? father.getTimezone() : "Asia/Jerusalem");
        LocalDate localDate = at.atZone(zone).toLocalDate();
        Instant dayStart = localDate.atStartOfDay(zone).toInstant();
        Instant dayEnd = localDate.plusDays(1).atStartOfDay(zone).toInstant();
        return reflectionRepository.countByFatherIdAndCreatedAtBetween(father.getId(), dayStart, dayEnd);
    }
}
