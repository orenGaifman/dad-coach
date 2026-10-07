package com.dadcoach.whatsapp.buttons;

import com.dadcoach.domain.child.Child;
import com.dadcoach.domain.child.ChildRepository;
import com.dadcoach.domain.father.Father;
import com.dadcoach.integration.platform.FatherTimezones;
import com.dadcoach.qualitytime.ActivityIdeas;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeService;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.qualitytime.SessionChildren;
import com.dadcoach.weeklyplan.WeeklyPlanContextBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A father's tap on a {@link SessionButton}, handled by Dad Coach before any AI turn (like Big Boss's follow-up
 * buttons):
 * <ul>
 *   <li>"היה מעולה": his session is completed here, exactly as the coach's complete_quality_time tool does, and a
 *       fixed confirmation with the week's progress goes back; already completed → "כבר רשום";</li>
 *   <li>"לא יצא": handed to the coach as his words "לא יצא" - its rules record it and offer another time;</li>
 *   <li>"רוצה רעיונות": 2-3 ideas for that session's children (fit for the youngest) that fit its length;</li>
 *   <li>a session that is not his, gone, cancelled or not started (for "done"): one fixed line, no AI turn;</li>
 *   <li>a "dc:" id this version does not know: the tapped title goes to the coach as a normal reply.</li>
 * </ul>
 */
@Component
public class SessionButtonTaps {

    private static final Logger log = LoggerFactory.getLogger(SessionButtonTaps.class);
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    static final String STALE_REPLY = IDENTITY + "הכפתור הזה כבר לא בתוקף, המפגש בוטל או השתנה. אפשר פשוט לכתוב לי 🙂";
    static final String ALREADY_DONE_REPLY = IDENTITY + "כבר רשום אצלי ✅";
    static final String MISSED_TEXT = "לא יצא";
    private static final int IDEAS = 3;

    /**
     * What to do with a tap: {@code reply} - send it (no AI turn); {@code coachText} - run the coach's turn with this
     * text instead of the title; both null - not a session button, run the normal turn.
     */
    public record Tap(String reply, String coachText) {
        static final Tap NOT_MINE = new Tap(null, null);
    }

    private final QualityTimeRepository sessions;
    private final QualityTimeService qualityTime;
    private final ChildRepository children;
    private final WeeklyPlanContextBuilder weeklyPlan;
    private final Clock clock;

    public SessionButtonTaps(QualityTimeRepository sessions, QualityTimeService qualityTime, ChildRepository children,
                             WeeklyPlanContextBuilder weeklyPlan, Clock clock) {
        this.sessions = sessions;
        this.qualityTime = qualityTime;
        this.children = children;
        this.weeklyPlan = weeklyPlan;
        this.clock = clock;
    }

    public Tap handle(Father father, String buttonId) {
        Optional<SessionButton> parsed = SessionButton.parse(buttonId);
        if (parsed.isEmpty()) {
            return Tap.NOT_MINE;
        }
        SessionButton button = parsed.get();
        Optional<QualityTime> own = sessions.findById(button.sessionId()).filter(qt -> father.getId().equals(qt.getFatherId()));
        String outcome = own.isEmpty() ? "NOT_HIS" : own.get().getStatus().name();
        log.atInfo().setMessage("whatsapp.button.tap").addKeyValue("action", button.action())
                .addKeyValue("fatherId", father.getId()).addKeyValue("session", outcome).log();
        if (own.isEmpty()) {
            return new Tap(STALE_REPLY, null);
        }
        QualityTime session = own.get();
        Instant now = clock.instant();
        return switch (button.action()) {
            case MISSED -> new Tap(null, MISSED_TEXT);
            case DONE -> {
                if (session.getStatus() == QualityTimeStatus.COMPLETED) {
                    yield new Tap(ALREADY_DONE_REPLY, null);
                }
                if (session.getStatus() != QualityTimeStatus.SCHEDULED || session.getScheduledStart().isAfter(now)) {
                    yield new Tap(STALE_REPLY, null);
                }
                qualityTime.completeQualityTime(session.getId(), null);
                yield new Tap(doneReply(father, childName(session)), null);
            }
            case IDEAS -> session.getStatus() != QualityTimeStatus.SCHEDULED || !session.getScheduledEnd().isAfter(now)
                    ? new Tap(STALE_REPLY, null) : new Tap(ideasReply(father, session), null);
        };
    }

    private String doneReply(Father father, String child) {
        StringBuilder reply = new StringBuilder(IDENTITY).append("איזה כיף! רשמתי את הזמן שלך");
        if (child != null) {
            reply.append(" עם ").append(child);
        }
        reply.append(" ✅");
        try {
            if (weeklyPlan.build(father).get("coverage") instanceof Map<?, ?> coverage
                    && coverage.get("completed_minutes") instanceof Number completed) {
                reply.append("\nהשבוע: ").append(hebrewDuration(completed.intValue()));
                if (coverage.get("target_minutes") instanceof Number target) {
                    reply.append(" מתוך ").append(hebrewDuration(target.intValue()));
                }
                reply.append(".");
            }
        } catch (RuntimeException e) {
            log.atWarn().setMessage("whatsapp.button.coverage_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
        return reply.toString();
    }

    private String ideasReply(Father father, QualityTime session) {
        LocalDate today = LocalDate.now(clock.withZone(FatherTimezones.of(father)));
        // a session with several children gets ideas that fit the youngest of them
        int age = childrenOf(session).stream().filter(c -> c.getBirthDate() != null).mapToInt(c -> c.ageOn(today))
                .min().orElse(6);
        String names = childName(session);
        int minutes = (int) Duration.between(session.getScheduledStart(), session.getScheduledEnd()).toMinutes();
        List<ActivityIdeas.ActivityIdea> ideas = ActivityIdeas.forChild(age, "he", null).stream()
                .sorted(Comparator.comparing(idea -> idea.durationMinutes() > minutes))
                .limit(IDEAS).toList();
        StringBuilder reply = new StringBuilder(IDENTITY).append("כמה רעיונות לזמן שלך");
        if (names != null) {
            reply.append(" עם ").append(names);
        }
        reply.append(":\n");
        for (ActivityIdeas.ActivityIdea idea : ideas) {
            reply.append("\n• ").append(idea.title()).append(" (").append(idea.durationMinutes()).append(" דק׳): ")
                    .append(idea.description());
        }
        return reply.append("\n\nבהצלחה! 💪").toString();
    }

    /**
     * All the session's children, joined in Hebrew ("מטר ונעם"). Read through the repository: a tap is handled
     * outside a transaction, where the session's first child is an unloaded proxy.
     */
    private String childName(QualityTime session) {
        Map<Long, String> names = new java.util.HashMap<>();
        childrenOf(session).forEach(c -> names.put(c.getId(), c.getName()));
        return SessionChildren.hebrew(session, names);
    }

    private List<Child> childrenOf(QualityTime session) {
        return children.findAllById(session.getChildIds());
    }

    static String hebrewDuration(int totalMinutes) {
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        String h = switch (hours) {
            case 0 -> "";
            case 1 -> "שעה";
            case 2 -> "שעתיים";
            default -> hours + " שעות";
        };
        if (minutes == 0) {
            return hours == 0 ? "0 דקות" : h;
        }
        if (hours == 0) {
            return minutes + " דקות";
        }
        return minutes == 30 ? h + " וחצי" : h + " ו-" + minutes + " דקות";
    }
}
