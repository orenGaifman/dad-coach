package com.dadcoach.integration;

import com.dadcoach.IntegrationTestBase;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.domain.father.FatherService;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.scheduling.SchedulingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: scheduling.
 * Daily coaching dispatch, quiet hours, inactivity detection.
 *
 * Verifies timezone-aware scheduling and inactivity detection with a real database.
 */
@Transactional
class SchedulingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private FatherService fatherService;

    @Autowired
    private FatherRepository fatherRepository;

    @Autowired
    private SchedulingService schedulingService;

    private Father father;

    @BeforeEach
    void setUp() {
        father = fatherService.createFather("+972503334444");
        fatherService.transitionStatus(father.getId(), FatherStatus.ONBOARDING, "Onboarding");
        father = fatherService.activateFather(father.getId());
        // Set preferred coaching time to 08:00 in Asia/Jerusalem
        father.setPreferredCoachingTime(LocalTime.of(8, 0));
        father.setTimezone("Asia/Jerusalem");
        father.setLastInteractionAt(Instant.now());
        fatherRepository.save(father);
    }

    @Test
    void dailyCoachingDispatch_matchesPreferredTime() {
        // Simulate "now" being 08:00 in Asia/Jerusalem
        ZoneId zone = ZoneId.of("Asia/Jerusalem");
        ZonedDateTime coaching800 = ZonedDateTime.of(LocalDate.now(), LocalTime.of(8, 0), zone);
        Instant now800 = coaching800.toInstant();

        List<Father> due = schedulingService.findFathersDueForDailyCoaching(now800);
        assertThat(due).extracting(Father::getId).contains(father.getId());

        // At a different time (e.g., 14:00), the father should NOT be due
        ZonedDateTime coaching1400 = ZonedDateTime.of(LocalDate.now(), LocalTime.of(14, 0), zone);
        Instant now1400 = coaching1400.toInstant();

        List<Father> notDue = schedulingService.findFathersDueForDailyCoaching(now1400);
        assertThat(notDue).extracting(Father::getId).doesNotContain(father.getId());
    }

    @Test
    void inactivityDetection_findsInactiveFathers() {
        // Set last interaction to 25 days ago (past the 21-day churn threshold)
        father.setLastInteractionAt(Instant.now().minus(Duration.ofDays(25)));
        fatherRepository.save(father);

        List<Father> inactive = schedulingService.findInactiveFathers(21);
        assertThat(inactive).extracting(Father::getId).contains(father.getId());

        // Fathers active within 21 days should NOT appear
        father.setLastInteractionAt(Instant.now().minus(Duration.ofDays(5)));
        fatherRepository.save(father);

        List<Father> active = schedulingService.findInactiveFathers(21);
        assertThat(active).extracting(Father::getId).doesNotContain(father.getId());
    }
}
