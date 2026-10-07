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

    static final String FOLLOW_UP_STATE = "SESSION_FOLLOW_UP";
    static final String REMINDER_STATE = "SESSION_REMINDER_1H";
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
        if (!FOLLOW_UP_STATE.equals(targetStateKey) && !REMINDER_STATE.equals(targetStateKey)) {
            return List.of();
        }
        Instant now = clock.instant();
        List<QualityTime> scheduled = sessions.findByFatherIdAndStatus(father.getId(), QualityTimeStatus.SCHEDULED);
        if (FOLLOW_UP_STATE.equals(targetStateKey)) {
            return scheduled.stream()
                    .filter(qt -> !qt.getScheduledEnd().isAfter(now) && qt.getScheduledEnd().isAfter(now.minus(FOLLOW_UP_WINDOW)))
                    .max(Comparator.comparing(QualityTime::getScheduledEnd))
                    .map(qt -> List.of(new SessionButton(SessionButton.Action.DONE, qt.getId()).toReplyButton(),
                            new SessionButton(SessionButton.Action.MISSED, qt.getId()).toReplyButton()))
                    .orElse(List.of());
        }
        return scheduled.stream()
                .filter(qt -> qt.getScheduledStart().isAfter(now) && qt.getScheduledStart().isBefore(now.plus(REMINDER_WINDOW)))
                .min(Comparator.comparing(QualityTime::getScheduledStart))
                .map(qt -> List.of(new SessionButton(SessionButton.Action.IDEAS, qt.getId()).toReplyButton()))
                .orElse(List.of());
    }
}
