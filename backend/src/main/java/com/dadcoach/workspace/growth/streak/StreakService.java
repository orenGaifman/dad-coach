package com.dadcoach.workspace.growth.streak;

import com.dadcoach.workspace.dto.response.StreakResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing father engagement streaks.
 *
 * <p>All calendar-day calculations use the father's configured timezone to ensure
 * correct streak tracking across time zones (Requirement 12.1).</p>
 */
@Service
@Transactional
public class StreakService {

    private static final Logger log = LoggerFactory.getLogger(StreakService.class);

    private final FatherStreakRepository fatherStreakRepository;
    private final Clock clock;

    public StreakService(FatherStreakRepository fatherStreakRepository) {
        this(fatherStreakRepository, Clock.systemUTC());
    }

    public StreakService(FatherStreakRepository fatherStreakRepository, Clock clock) {
        this.fatherStreakRepository = fatherStreakRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FatherStreak getStreak(UUID fatherId) {
        return fatherStreakRepository.findByFatherId(fatherId)
                .orElseGet(() -> {
                    FatherStreak newStreak = new FatherStreak(fatherId);
                    return fatherStreakRepository.save(newStreak);
                });
    }

    @Transactional(readOnly = true)
    public StreakResponse getStreakResponse(UUID fatherId) {
        FatherStreak streak = fatherStreakRepository.findByFatherId(fatherId)
                .orElse(new FatherStreak(fatherId));
        boolean atRisk = isStreakAtRisk(fatherId);

        return StreakResponse.builder()
                .currentStreakDays(streak.getCurrentStreakDays())
                .longestStreakDays(streak.getLongestStreakDays())
                .streakStartDate(streak.getStreakStartDate())
                .lastQualifyingInteractionDate(streak.getLastQualifyingDate())
                .streakAtRisk(atRisk)
                .build();
    }

    /**
     * Records a qualifying interaction and updates the streak.
     *
     * <p>If the interaction is on the same calendar day as the last one (in the father's timezone),
     * this is a no-op. If it's the next consecutive day, the streak increments.
     * Otherwise, a new streak starts.</p>
     *
     * <p>Concurrency note: Multiple threads calling this for the same father on the same day
     * will not produce inconsistent state because the same-day check returns early, and the
     * father_streaks UNIQUE constraint on father_id prevents duplicate records.</p>
     *
     * @param fatherId  the father's unique identifier
     * @param timestamp the time of the qualifying interaction
     * @return the current streak day count after recording
     */
    public int recordQualifyingInteraction(UUID fatherId, Instant timestamp) {
        FatherStreak streak = fatherStreakRepository.findByFatherId(fatherId)
                .orElse(new FatherStreak(fatherId));
        ZoneId fatherZone = ZoneId.of(streak.getTimezone());
        LocalDate interactionDate = timestamp.atZone(fatherZone).toLocalDate();
        LocalDate lastDate = streak.getLastQualifyingDate();

        if (lastDate != null && lastDate.equals(interactionDate)) {
            log.debug("Streak already recorded today for father {}", fatherId);
            return streak.getCurrentStreakDays();
        }

        if (lastDate != null && lastDate.equals(interactionDate.minusDays(1))) {
            streak.setCurrentStreakDays(streak.getCurrentStreakDays() + 1);
            log.debug("Streak incremented to {} for father {}", streak.getCurrentStreakDays(), fatherId);
        } else {
            streak.setCurrentStreakDays(1);
            streak.setStreakStartDate(interactionDate);
            log.debug("New streak started for father {}", fatherId);
        }

        if (streak.getCurrentStreakDays() > streak.getLongestStreakDays()) {
            streak.setLongestStreakDays(streak.getCurrentStreakDays());
        }

        streak.setLastQualifyingDate(interactionDate);

        try {
            fatherStreakRepository.saveAndFlush(streak);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Concurrent insert for same father — reload and return current state
            log.debug("Concurrent streak update for father {} — reloading", fatherId);
            streak = fatherStreakRepository.findByFatherId(fatherId).orElse(streak);
        }

        return streak.getCurrentStreakDays();
    }

    public void resetExpiredStreaks() {
        List<FatherStreak> allStreaks = fatherStreakRepository.findAll();
        int resetCount = 0;

        for (FatherStreak streak : allStreaks) {
            if (streak.getCurrentStreakDays() == 0) {
                continue;
            }

            ZoneId fatherZone = ZoneId.of(streak.getTimezone());
            LocalDate today = Instant.now(clock).atZone(fatherZone).toLocalDate();
            LocalDate yesterday = today.minusDays(1);

            if (streak.getLastQualifyingDate() == null || streak.getLastQualifyingDate().isBefore(yesterday)) {
                streak.setCurrentStreakDays(0);
                streak.setStreakStartDate(null);
                fatherStreakRepository.save(streak);
                resetCount++;
                log.debug("Reset expired streak for father {} (last qualifying: {})",
                        streak.getFatherId(), streak.getLastQualifyingDate());
            }
        }

        if (resetCount > 0) {
            log.info("Reset {} expired streaks", resetCount);
        }
    }

    @Transactional(readOnly = true)
    public boolean isStreakAtRisk(UUID fatherId) {
        FatherStreak streak = fatherStreakRepository.findByFatherId(fatherId).orElse(null);

        if (streak == null || streak.getCurrentStreakDays() == 0) {
            return false;
        }

        ZoneId fatherZone = ZoneId.of(streak.getTimezone());
        ZonedDateTime nowInFatherZone = Instant.now(clock).atZone(fatherZone);
        LocalDate today = nowInFatherZone.toLocalDate();
        LocalTime currentTime = nowInFatherZone.toLocalTime();

        boolean noInteractionToday = streak.getLastQualifyingDate() == null
                || !streak.getLastQualifyingDate().equals(today);
        boolean pastEvening = currentTime.isAfter(LocalTime.of(18, 0));

        return noInteractionToday && pastEvening;
    }
}
