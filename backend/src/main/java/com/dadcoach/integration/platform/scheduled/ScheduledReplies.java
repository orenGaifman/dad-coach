package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.qualitytime.SessionChildren;
import com.dadcoach.replies.ClaimGuard;
import com.dadcoach.replies.CoachReplies;
import com.dadcoach.replies.HebrewWhen;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The session timers' messages, from data (D-036; findings D-1, D-11, D-15). The scheduled turn's model decides whether
 * to write; what it writes about a session must be this message:
 * <ul>
 *   <li>no valid session for the timer (cancelled on his page, moved, already done): nothing is sent, whatever the
 *       model wrote - a reminder about a session that is not there would be false;</li>
 *   <li>the model's text is sent when it says the same words; the hour-before may end with a good wish for the activity
 *       he named today instead of the question ("בהצלחה עם הבישול!" - D-15); anything else is replaced by the ready
 *       message;</li>
 *   </ul>
 * Applied in {@link ScheduledResponseController} before delivery (the per-message templates outside the window are
 * another session's work - D-11).
 * The session is picked the way the weekly plan and the timers' buttons pick it (SessionButtonOffers).
 */
@Component
public class ScheduledReplies {

    static final String MORNING = "SESSION_MORNING_REMINDER";
    static final String HOUR_BEFORE = "SESSION_REMINDER_1H";
    static final String FOLLOW_UP = "SESSION_FOLLOW_UP";
    static final Duration REMINDER_WINDOW = Duration.ofHours(3);
    static final Duration FOLLOW_UP_WINDOW = Duration.ofHours(12);

    /**
     * @param text     the message without the identity line; null - no valid session, send nothing
     * @param sessions the sessions it names (noted as mentioned today)
     */
    public record Planned(String text, List<UUID> sessions) {
    }

    private final QualityTimeRepository sessions;
    private final com.dadcoach.domain.child.ChildRepository children;
    private final WeeklyGoalService goals;
    private final Clock clock;

    public ScheduledReplies(QualityTimeRepository sessions, com.dadcoach.domain.child.ChildRepository children,
                            WeeklyGoalService goals, Clock clock) {
        this.sessions = sessions;
        this.children = children;
        this.goals = goals;
        this.clock = clock;
    }

    /** The planned message for a session timer's state; empty for any other state (the daily check is the model's). */
    @Transactional(readOnly = true)
    public Optional<Planned> plan(Father father, String targetStateKey) {
        if (!MORNING.equals(targetStateKey) && !HOUR_BEFORE.equals(targetStateKey) && !FOLLOW_UP.equals(targetStateKey)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        ZoneId zone = goals.zoneFor(father);
        LocalDate today = now.atZone(zone).toLocalDate();
        List<QualityTime> scheduled = sessions.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.SCHEDULED);
        Planned none = new Planned(null, List.of());
        return Optional.of(switch (targetStateKey) {
            case MORNING -> {
                List<QualityTime> later = scheduled.stream()
                        .filter(qt -> qt.getScheduledEnd().isAfter(now) && qt.getScheduledStart().atZone(zone).toLocalDate().equals(today))
                        .sorted(Comparator.comparing(QualityTime::getScheduledStart)).toList();
                if (later.isEmpty()) {
                    yield none;
                }
                List<String> times = new ArrayList<>();
                LinkedHashSet<String> names = new LinkedHashSet<>();
                for (QualityTime qt : later) {
                    times.add(HebrewWhen.time(qt.getScheduledStart().atZone(zone).toLocalTime()));
                    names.addAll(names(qt));
                }
                String kids = SessionChildren.joinHebrew(new ArrayList<>(names));
                yield new Planned(CoachReplies.morning(times, kids), later.stream().map(QualityTime::getId).toList());
            }
            case HOUR_BEFORE -> scheduled.stream()
                    .filter(qt -> qt.getScheduledStart().isAfter(now) && qt.getScheduledStart().isBefore(now.plus(REMINDER_WINDOW)))
                    .min(Comparator.comparing(QualityTime::getScheduledStart))
                    .map(qt -> {
                        String kids = SessionChildren.joinHebrew(names(qt));
                        return new Planned(CoachReplies.text(CoachReplies.hourBefore(kids)), List.of(qt.getId()));
                    }).orElse(none);
            default -> scheduled.stream()
                    .filter(qt -> !qt.getScheduledEnd().isAfter(now) && qt.getScheduledEnd().isAfter(now.minus(FOLLOW_UP_WINDOW)))
                    .max(Comparator.comparing(QualityTime::getScheduledEnd))
                    .map(qt -> {
                        String kids = SessionChildren.joinHebrew(names(qt));
                        return new Planned(CoachReplies.followUp(kids), List.of(qt.getId()));
                    }).orElse(none);
        });
    }

    /** The model's message when it says the planned words (the hour-before may end with a wish instead), else the plan. */
    public static String choose(String modelBody, Planned planned, String targetStateKey) {
        String model = modelBody == null ? "" : modelBody.strip();
        if (ClaimGuard.words(model).equals(ClaimGuard.words(planned.text()))) {
            return model;
        }
        if (HOUR_BEFORE.equals(targetStateKey)) {
            List<String> lines = model.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
            String first = planned.text().lines().findFirst().orElse("");
            if (lines.size() == 2 && ClaimGuard.words(lines.get(0)).equals(ClaimGuard.words(first))
                    && lines.get(1).startsWith("בהצלחה") && lines.get(1).length() <= 60 && !lines.get(1).contains("?")) {
                return model;
            }
        }
        return planned.text();
    }

    private List<String> names(QualityTime qt) {
        Map<Long, String> byId = new java.util.HashMap<>();
        children.findAllById(qt.getChildIds()).forEach(c -> byId.put(c.getId(), c.getName()));
        return SessionChildren.names(qt, byId);
    }
}
