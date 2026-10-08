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
import com.dadcoach.weeklyplan.HebrewHours;
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
 *   <li>"רוצה רעיונות": 3 one-line ideas for that session's children (fit for the youngest, the longer ones last),
 *       with their names (D-032);</li>
 *   <li>a session that is not his, gone, cancelled, ended or not started (for "done"): a fixed reply that says
 *       which of these it is, no AI turn;</li>
 *   <li>a "dc:" id this version does not know: the tapped title goes to the coach as a normal reply.</li>
 * </ul>
 */
@Component
public class SessionButtonTaps {

    private static final Logger log = LoggerFactory.getLogger(SessionButtonTaps.class);
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    private static final String WRITE_TO_ME = "\nאפשר פשוט לכתוב לי 🙂";
    /** D-032: a button that no longer works says why, one fact per line - the true reason, never a guess. */
    static final String STALE_REPLY = IDENTITY + "הכפתור הזה כבר לא בתוקף." + WRITE_TO_ME;
    static final String CANCELLED_REPLY = IDENTITY + "המפגש הזה בוטל או הוזז." + WRITE_TO_ME;
    static final String MISSED_REPLY = IDENTITY + "המפגש הזה רשום אצלי כמפגש שלא יצא." + WRITE_TO_ME;
    static final String NOT_STARTED_REPLY = IDENTITY + "המפגש הזה עוד לא התחיל.\nאחרי שיתחיל, הכפתור יעבוד 🙂";
    static final String ENDED_REPLY = IDENTITY + "המפגש הזה כבר נגמר." + WRITE_TO_ME;
    static final String ALREADY_DONE_REPLY = IDENTITY + "את המפגש הזה כבר רשמתי 🙂";
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
                if (session.getStatus() != QualityTimeStatus.SCHEDULED) {
                    yield new Tap(closedReply(session), null);
                }
                if (session.getScheduledStart().isAfter(now)) {
                    yield new Tap(NOT_STARTED_REPLY, null);
                }
                // B-1: a belt earned by this tap is told here, once (the weekly job no longer promotes - D-030)
                var completed = qualityTime.completeQualityTime(session.getId(), null);
                yield new Tap(doneReply(father, childName(session),
                        completed.beltEarned() == null ? null : completed.beltEarned().getDisplayName("he")), null);
            }
            case IDEAS -> session.getStatus() != QualityTimeStatus.SCHEDULED ? new Tap(closedReply(session), null)
                    : !session.getScheduledEnd().isAfter(now) ? new Tap(ENDED_REPLY, null)
                    : new Tap(ideasReply(father, session), null);
        };
    }

    /** Why a session that is no longer SCHEDULED takes no tap. */
    private static String closedReply(QualityTime session) {
        return switch (session.getStatus()) {
            case CANCELLED -> CANCELLED_REPLY;
            case MISSED -> MISSED_REPLY;
            case COMPLETED -> ENDED_REPLY;
            case SCHEDULED -> STALE_REPLY;
        };
    }

    /** The same lines as complete_quality_time's reply (D-034): what was recorded, the week, a belt only when earned. */
    private String doneReply(Father father, String child, String beltEarned) {
        com.dadcoach.replies.CoachReplies.Week week = null;
        try {
            week = com.dadcoach.replies.CoachReplies.Week.of(weeklyPlan.build(father).get("coverage"));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("whatsapp.button.coverage_failed").addKeyValue("error", e.getClass().getSimpleName()).log();
        }
        return IDENTITY + com.dadcoach.replies.CoachReplies.text(com.dadcoach.replies.CoachReplies.done(child, week, beltEarned));
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
        // D-032: one short line per idea with the child's name, no minutes (they rarely matched the session)
        String length = hebrewDuration(minutes);
        StringBuilder reply = new StringBuilder(IDENTITY).append(ideas.size()).append(" רעיונות ל")
                .append(Character.isDigit(length.charAt(0)) ? "-" : "").append(length);
        if (names != null) {
            reply.append(" עם ").append(names);
        }
        reply.append(":");
        ActivityIdeas.Form form = formOf(childrenOf(session));
        for (ActivityIdeas.ActivityIdea idea : ideas) {
            reply.append("\n• ").append(ActivityIdeas.line(idea, names, form));
        }
        return reply.append("\n\nתספר לי אחר כך איך היה 🙂").toString();
    }

    /** The verb form of the session's children: one boy, one girl, several, or one whose gender is not known. */
    static ActivityIdeas.Form formOf(List<Child> kids) {
        if (kids.size() > 1) {
            return ActivityIdeas.Form.SEVERAL;
        }
        String gender = kids.isEmpty() ? null : kids.get(0).getGender();
        return "girl".equals(gender) ? ActivityIdeas.Form.GIRL
                : "boy".equals(gender) ? ActivityIdeas.Form.BOY : ActivityIdeas.Form.UNKNOWN;
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
        return HebrewHours.of(totalMinutes);
    }
}
