package com.dadcoach.whatsapp.buttons;

import com.dadcoach.channel.dto.OutboundMessageDto.ReplyButton;
import com.dadcoach.domain.father.Father;
import com.dadcoach.qualitytime.QualityTime;
import com.dadcoach.qualitytime.QualityTimeRepository;
import com.dadcoach.qualitytime.QualityTimeStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The buttons under a coach message the platform's session timers produce. The callback names only the state the
 * timer woke ({@code targetStateKey}), so the session is picked the way the coach's weekly plan context picks it:
 * <ul>
 *   <li>SESSION_FOLLOW_UP ("נו, איך היה?"): the most recently ended session still SCHEDULED (awaiting confirmation)
 *       → "היה מעולה" / "לא יצא";</li>
 *   <li>SESSION_REMINDER_1H ("עוד שעה..."): the next session that has not started yet → "רוצה רעיונות".</li>
 * </ul>
 * Any other message, or no such session: no buttons.
 */
@Component
public class SessionButtonOffers {

    public static final String FOLLOW_UP_STATE = "SESSION_FOLLOW_UP";
    public static final String REMINDER_STATE = "SESSION_REMINDER_1H";
    /** The coach asks only about a session that ended up to about three hours ago; a wider net costs nothing. */
    static final Duration FOLLOW_UP_WINDOW = Duration.ofHours(12);
    /** The reminder fires about an hour before; anything starting later is not the session it is about. */
    static final Duration REMINDER_WINDOW = Duration.ofHours(3);

    private final QualityTimeRepository sessions;
    private final Clock clock;

    public SessionButtonOffers(QualityTimeRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    public List<ReplyButton> forScheduledMessage(Father father, String targetStateKey) {
        return sessionFor(father, targetStateKey).map(qt -> FOLLOW_UP_STATE.equals(targetStateKey)
                        ? List.of(new SessionButton(SessionButton.Action.DONE, qt.getId()).toReplyButton(),
                                new SessionButton(SessionButton.Action.MISSED, qt.getId()).toReplyButton())
                        : List.of(new SessionButton(SessionButton.Action.IDEAS, qt.getId()).toReplyButton()))
                .orElse(List.of());
    }

    /** The session a follow-up or a 1-hour reminder is about (the buttons' and the message's own template's). */
    public Optional<QualityTime> sessionFor(Father father, String targetStateKey) {
        if (!FOLLOW_UP_STATE.equals(targetStateKey) && !REMINDER_STATE.equals(targetStateKey)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        List<QualityTime> scheduled = sessions.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.SCHEDULED);
        if (FOLLOW_UP_STATE.equals(targetStateKey)) {
            return scheduled.stream()
                    .filter(qt -> !qt.getScheduledEnd().isAfter(now) && qt.getScheduledEnd().isAfter(now.minus(FOLLOW_UP_WINDOW)))
                    .max(Comparator.comparing(QualityTime::getScheduledEnd));
        }
        return scheduled.stream()
                .filter(qt -> qt.getScheduledStart().isAfter(now) && qt.getScheduledStart().isBefore(now.plus(REMINDER_WINDOW)))
                .min(Comparator.comparing(QualityTime::getScheduledStart));
    }
}
