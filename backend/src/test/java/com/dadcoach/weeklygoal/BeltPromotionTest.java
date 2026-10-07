package com.dadcoach.weeklygoal;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sunday's weekly completion closes the week (status, streak) but never moves the belt: belts come only from completed
 * sessions (Belt.fromCompletionCount), as the site, the dashboard and the coach say. It used to promote one belt per
 * met week (the old 7-week program) and announce it with a message about that program.
 */
class BeltPromotionTest extends AbstractIntegrationTest {

    @Autowired WeeklyGoalCompletionJob job;
    @Autowired WeeklyGoalService goals;

    @Test
    void aMetWeekKeepsTheBeltAndSendsNoPromotion() {
        Father f = data.activeFather("+19995550600");
        goals.createAndActivateWeeklyGoal(f.getId(), 1);
        goals.recordCompletedQualityTime(f.getId(), clock.instant(), 70);
        String beltBefore = jdbc.queryForObject("SELECT current_belt FROM father WHERE id = ?", String.class, f.getId());

        clock.set(Instant.parse("2026-11-08T06:00:00Z")); // the next Sunday
        data.endpoint(f, true);
        job.run();

        assertThat(jdbc.queryForObject("SELECT current_belt FROM father WHERE id = ?", String.class, f.getId())).isEqualTo(beltBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM weekly_goal WHERE father_id = ?", String.class, f.getId())).isEqualTo("COMPLETED");
        assertThat(fake.metaSends()).isEmpty();
        assertThat(fake.recordedOutbound()).isEmpty();
    }
}
