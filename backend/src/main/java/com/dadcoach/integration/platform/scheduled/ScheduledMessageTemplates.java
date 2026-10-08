package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.channel.template.TemplateCall;
import com.dadcoach.channel.template.WhatsAppTemplateCatalog;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import com.dadcoach.qualitytime.SessionChildren;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import com.dadcoach.whatsapp.buttons.SessionButton;
import com.dadcoach.whatsapp.buttons.SessionButtonOffers;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The own template of a session timer's message (morning of the session, an hour before, the follow-up), for when
 * his 24-hour window is closed. The values come from the session the message is about — the same session the
 * in-window buttons and the coach's weekly plan pick — never from the AI's text. No session found (or any other
 * state): empty, and the message goes out in the general template.
 */
@Component
public class ScheduledMessageTemplates {

    public static final String MORNING_STATE = "SESSION_MORNING_REMINDER";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final SessionButtonOffers offers;
    private final QualityTimeRepository sessions;
    private final WeeklyGoalService weeklyGoals;
    private final Clock clock;

    public ScheduledMessageTemplates(SessionButtonOffers offers, QualityTimeRepository sessions,
                                     WeeklyGoalService weeklyGoals, Clock clock) {
        this.offers = offers;
        this.sessions = sessions;
        this.weeklyGoals = weeklyGoals;
        this.clock = clock;
    }

    /** One read-only transaction: the session's children load lazily. */
    @Transactional(readOnly = true)
    public Optional<TemplateCall> forScheduledMessage(Father father, String targetStateKey) {
        if (MORNING_STATE.equals(targetStateKey)) {
            return morning(father);
        }
        if (SessionButtonOffers.REMINDER_STATE.equals(targetStateKey)) {
            return offers.sessionFor(father, targetStateKey).flatMap(qt -> children(qt).map(names ->
                    new TemplateCall(WhatsAppTemplateCatalog.SESSION_HOUR_BEFORE_HE, List.of(names),
                            List.of(new SessionButton(SessionButton.Action.IDEAS, qt.getId()).id()))));
        }
        if (SessionButtonOffers.FOLLOW_UP_STATE.equals(targetStateKey)) {
            return offers.sessionFor(father, targetStateKey).flatMap(qt -> children(qt).map(names ->
                    new TemplateCall(WhatsAppTemplateCatalog.SESSION_FOLLOW_UP_HE, List.of(names),
                            List.of(new SessionButton(SessionButton.Action.DONE, qt.getId()).id(),
                                    new SessionButton(SessionButton.Action.MISSED, qt.getId()).id()))));
        }
        return Optional.empty();
    }

    /**
     * Today's sessions that have not ended (the weekly plan's sessions_today): their times, earliest first ("17:00",
     * "17:00 ו-19:00"), and every child in them ("מאיה ויובל").
     */
    private Optional<TemplateCall> morning(Father father) {
        Instant now = clock.instant();
        ZoneId zone = weeklyGoals.zoneFor(father);
        LocalDate today = now.atZone(zone).toLocalDate();
        List<QualityTime> todays = sessions.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.SCHEDULED).stream()
                .filter(qt -> qt.getScheduledStart() != null && qt.getScheduledEnd() != null)
                .filter(qt -> qt.getScheduledEnd().isAfter(now) && qt.getScheduledStart().atZone(zone).toLocalDate().equals(today))
                .sorted(Comparator.comparing(QualityTime::getScheduledStart))
                .toList();
        if (todays.isEmpty()) {
            return Optional.empty();
        }
        Set<String> times = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (QualityTime qt : todays) {
            times.add(qt.getScheduledStart().atZone(zone).format(HH_MM));
            names.addAll(SessionChildren.names(qt));
        }
        if (names.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TemplateCall.of(WhatsAppTemplateCatalog.SESSION_MORNING_HE,
                joinTimes(new ArrayList<>(times)), SessionChildren.joinHebrew(new ArrayList<>(names))));
    }

    /** "17:00", "17:00 ו-19:00", "10:00, 17:00 ו-19:00". */
    static String joinTimes(List<String> times) {
        if (times.size() == 1) {
            return times.get(0);
        }
        return String.join(", ", times.subList(0, times.size() - 1)) + " ו-" + times.get(times.size() - 1);
    }

    private static Optional<String> children(QualityTime qt) {
        return Optional.ofNullable(SessionChildren.hebrew(qt)).filter(s -> !s.isBlank());
    }
}
