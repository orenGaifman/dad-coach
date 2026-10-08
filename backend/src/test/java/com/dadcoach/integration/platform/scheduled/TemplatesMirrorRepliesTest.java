package com.dadcoach.integration.platform.scheduled;

import static org.assertj.core.api.Assertions.assertThat;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.channel.template.TemplateCall;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A session timer's message reads the same inside and outside the 24-hour window: the template filled from the
 * session ({@link ScheduledMessageTemplates}) is, word for word, the message built in code ({@link ScheduledReplies}).
 */
class TemplatesMirrorRepliesTest extends AbstractIntegrationTest {

    @Autowired ScheduledReplies replies;
    @Autowired ScheduledMessageTemplates templates;
    @Autowired QualityTimeRepository sessions;

    private Father father;

    private void session(Child child, Duration fromNow, int minutes) {
        Instant start = clock.instant().plus(fromNow);
        sessions.saveAndFlush(new QualityTime(father, child, start, start.plus(Duration.ofMinutes(minutes))));
    }

    private void assertSame(String state) {
        String planned = replies.plan(father, state).orElseThrow().text();
        TemplateCall call = templates.forScheduledMessage(father, state).orElseThrow();
        assertThat(call.text()).as(state).isEqualTo(WhatsAppTemplateCatalog.IDENTITY + planned);
    }

    @Test
    @DisplayName("the morning reminder with two sessions and two children, the hour before and the follow-up")
    void sameWords() {
        father = data.activeFather("+19995550800");
        Child noa = data.child(father, "נועה", 6);
        Child yuval = data.child(father, "יובל", 8);
        session(noa, Duration.ofHours(3), 60);   // 15:00 local (the clock is Tuesday 12:00 in Israel)
        session(yuval, Duration.ofHours(5), 60); // 17:00
        assertSame(ScheduledMessageTemplates.MORNING_STATE);

        father = data.activeFather("+19995550801");
        session(data.child(father, "מאיה", 5), Duration.ofMinutes(60), 45);
        assertSame("SESSION_REMINDER_1H");

        father = data.activeFather("+19995550802");
        session(data.child(father, "איתמר", 9), Duration.ofMinutes(-90), 60);
        assertSame("SESSION_FOLLOW_UP");
    }
}
