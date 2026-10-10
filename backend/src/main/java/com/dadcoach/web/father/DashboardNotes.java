package com.dadcoach.web.father;

import com.dadcoach.integration.platform.timeline.TimelineReports;

import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.SentMessageRecorder;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.SessionChildren;
import com.dadcoach.replies.HebrewWhen;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the father did on his page, noted in his WhatsApp conversation with the coach (D-036, the Big Boss D-180
 * pattern): he cancelled a session on the page, then wrote "מה יש לי השבוע?" - the weekly plan is fresh, but the chat
 * history still said "קבעתי ... מחר ב-17:00", and in a long chat the model trusts its history. The note is history only
 * (never sent on WhatsApp - he did it himself, he knows), Hebrew, and opens with "📊 בדף שלך:" so it never reads like
 * something the coach wrote to him. A failure never fails the page's action.
 */
@Component
public class DashboardNotes {

    private static final Logger log = LoggerFactory.getLogger(DashboardNotes.class);
    static final String PREFIX = "📊 בדף שלך: ";

    private final SentMessageRecorder recorder;
    private final QualityTimeRepository sessions;
    private final WeeklyGoalService goals;
    private final Clock clock;
    private TimelineReports timeline;

    public DashboardNotes(SentMessageRecorder recorder, QualityTimeRepository sessions, WeeklyGoalService goals, Clock clock) {
        this.recorder = recorder;
        this.sessions = sessions;
        this.goals = goals;
        this.clock = clock;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTimeline(TimelineReports timeline) {
        this.timeline = timeline;
    }

    /** After the page confirmed or cancelled a session (the change is committed). */
    @Transactional(readOnly = true)
    public void session(Father father, UUID sessionId, String beltEarned) {
        QualityTime qt = sessions.findById(sessionId).orElse(null);
        if (qt == null) {
            return;
        }
        var zone = goals.zoneFor(father);
        String when = HebrewWhen.label(qt.getScheduledStart().atZone(zone), clock.instant().atZone(zone).toLocalDate());
        String children = SessionChildren.hebrew(qt);
        String with = children == null ? "" : " עם " + children;
        String text = switch (qt.getStatus()) {
            case COMPLETED -> "סימנת שהמפגש של *" + when + "*" + with + " היה."
                    + (beltEarned == null ? "" : "\nעלית ל*" + beltEarned + "*.");
            case CANCELLED -> "ביטלת את המפגש של *" + when + "*" + with + ".";
            case MISSED -> "סימנת שהמפגש של *" + when + "*" + with + " לא יצא.";
            case SCHEDULED -> null;
        };
        if (text != null) {
            note(father, "session-" + qt.getStatus().name().toLowerCase() + ":" + sessionId, text);
        }
    }

    public void childAdded(Father father, String name, int age) {
        note(father, "child-added:" + name, "הוספת את " + name + ", בגיל " + age + ".");
    }

    public void childUpdated(Father father, String oldName, String name, int age) {
        String text = oldName != null && !oldName.equals(name)
                ? "עדכנת את " + oldName + ": השם עכשיו " + name + ", בגיל " + age + "."
                : "עדכנת את " + name + ": בגיל " + age + ".";
        note(father, "child-updated:" + name, text);
    }

    public void nameChanged(Father father, String name) {
        note(father, "name:" + name, "עדכנת את השם שלך ל" + name + ".");
    }

    private void note(Father father, String key, String text) {
        String correlationId = "dashboard:" + key + ":" + clock.instant().toEpochMilli();
        try {
            if (timeline != null && timeline.enabled()) {
                // D-039: history only - a kind and no delivery status (it was never sent on WhatsApp)
                timeline.outbound(TimelineReports.Person.of(father), TimelineReports.Part.sent(correlationId, PREFIX + text,
                        null, TimelineReports.KIND_DASHBOARD_NOTE));
                return;
            }
            recorder.recordSent(father, PREFIX + text, correlationId);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("dashboard.note.failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }
}
