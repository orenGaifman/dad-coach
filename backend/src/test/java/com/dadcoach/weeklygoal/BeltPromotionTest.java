package com.dadcoach.weeklygoal;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sunday's weekly completion: a met goal promotes the belt; the father is told through the channel layer and the
 * message is recorded in his platform conversation (playbook §10.2), so his "כן" to it is understood.
 */
class BeltPromotionTest extends AbstractIntegrationTest {

    @Autowired WeeklyGoalCompletionJob job;
    @Autowired WeeklyGoalService goals;
    @Autowired ObjectMapper json;

    @Test
    void aMetGoalPromotesTellsHimAndRecordsTheMessage() throws Exception {
        Father f = data.activeFather("+19995550600");
        goals.createAndActivateWeeklyGoal(f.getId(), 1);
        goals.recordCompletedQualityTime(f.getId(), clock.instant(), 70);

        clock.set(Instant.parse("2026-11-08T06:00:00Z")); // the next Sunday
        data.endpoint(f, true); // he wrote within the last 24 hours
        job.run();

        assertThat(jdbc.queryForObject("SELECT current_belt FROM father WHERE id = ?", String.class, f.getId())).isNotEqualTo("WHITE");
        // the belt image (window open) and the text
        assertThat(fake.metaSends()).hasSize(2);
        assertThat(json.readTree(fake.metaSends().get(0).body()).path("type").asText()).isEqualTo("image");
        assertThat(json.readTree(fake.metaSends().get(0).body()).path("image").path("link").asText())
                .startsWith("https://app.dadcoach.test/belts/");
        String text = json.readTree(fake.metaSends().get(1).body()).path("text").path("body").asText();
        assertThat(text).contains("עלית חגורה");

        assertThat(fake.recordedOutbound()).hasSize(1);
        JsonNode recorded = json.readTree(fake.recordedOutbound().get(0).body());
        assertThat(recorded.path("workflowKey").asText()).isEqualTo("dad-coach-3");
        assertThat(recorded.path("userId").asText()).isEqualTo("whatsapp:" + f.getPhone());
        assertThat(recorded.path("content").asText()).isEqualTo(text);
        assertThat(recorded.path("metadata").path("timezone").asText()).isEqualTo("Asia/Jerusalem");

        job.run(); // a rerun completes nothing new and sends nothing
        assertThat(fake.metaSends()).hasSize(2);
    }

    @Test
    void outsideTheWindowTheTextGoesAsTheTemplateAndNoImage() throws Exception {
        Father f = data.activeFather("+19995550601");
        data.endpoint(f, false);
        goals.createAndActivateWeeklyGoal(f.getId(), 1);
        goals.recordCompletedQualityTime(f.getId(), clock.instant(), 60);
        clock.set(Instant.parse("2026-11-08T06:00:00Z"));
        job.run();
        assertThat(fake.metaSends()).hasSize(1);
        assertThat(json.readTree(fake.metaSends().get(0).body()).path("type").asText()).isEqualTo("template");
        assertThat(fake.recordedOutbound()).hasSize(1);
    }
}
