package com.dadcoach.weeklygoal;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.config.BeltImageConfig;
import com.dadcoach.domain.father.Father;
import com.dadcoach.support.FakeServers;
import com.dadcoach.workflow.Belt;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
    @Autowired BeltPromotionNotifier notifier;
    @Autowired BeltImageConfig images;

    @AfterEach
    void noImages() {
        images.setYellow("");
    }

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

    @Test
    void aRefusedBeltImageStillSendsAndRecordsThePromotionText() {
        // Meta refuses the image (HTTP 500 - the client throws); every other send is accepted
        AtomicInteger n = new AtomicInteger();
        fake.onMetaSend(c -> c.body().contains("\"type\":\"image\"")
                ? new FakeServers.Reply(500, "{\"error\":{\"message\":\"media download failed\"}}")
                : FakeServers.Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.belt."
                        + n.incrementAndGet() + "\"}]}"));
        images.setYellow("https://cdn.example/yellow.png");
        Father f = data.activeFather("+19995550601");
        data.endpoint(f, true); // his window is open: the image is tried

        notifier.sendPromotionNotification(new WeeklyGoalService.BeltPromotionResult(f.getId(), true, Belt.WHITE,
                Belt.YELLOW, 120, 120, 1, false));

        assertThat(fake.metaSends()).hasSize(2);
        assertThat(fake.metaSends().get(0).body()).contains("\"type\":\"image\"");
        assertThat(fake.metaSends().get(1).body()).contains("\"type\":\"text\"").contains("כל מפגש שקרה ואישרת נספר");
        assertThat(fake.recordedOutbound()).hasSize(1); // the legacy record (timeline reports off)
        assertThat(fake.recordedOutbound().get(0).body()).contains("belt-promotion:" + f.getId() + ":YELLOW");
    }
}
