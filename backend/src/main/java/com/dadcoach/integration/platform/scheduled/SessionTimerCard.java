package com.dadcoach.integration.platform.scheduled;

import com.dadcoach.domain.father.Father;
import com.dadcoach.weeklygoal.WeeklyGoalService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-049 (Unified Workflow Phase 5.3 slice D): a session timer's message decided from data, for the platform's
 * {@code dc_session_timer_card} tool - the reply of the tool-only {@code SESSION_MORNING_REMINDER} and
 * {@code SESSION_FOLLOW_UP} turns (no model call), and the guard of the {@code SESSION_REMINDER_1H} AI turn.
 *
 * <p>The text is {@link ScheduledReplies#plan}'s - the very message the callback sends today whatever the model wrote
 * (D-036) - so a tool-only turn sends the same words with the same buttons. The turn sends nothing exactly where the
 * callback or the state's own rules made it send nothing before:</p>
 * <ul>
 *   <li>{@code NO_VALID_SESSION} - the plan is empty (cancelled, moved, done): the callback's own skip. The only
 *       reason for the hour-before (its other rules stay the model's: it still runs, and keeps its wish - D-15);</li>
 *   <li>{@code ALREADY_REMINDED_TODAY} (morning) - every session the morning names was already in today's morning
 *       reminder (two sessions on one day wake the morning twice, both at 08:00): the rule
 *       {@code quiet-without-session-today}, "this conversation already reminded him today about these sessions";</li>
 *   <li>{@code NOT_JUST_ENDED} (follow-up) - the session ended more than {@link #FOLLOW_UP_FRESH} ago (a late timer):
 *       the rule {@code ask-only-when-awaiting}, "ended N min ago with N up to about 180";</li>
 *   <li>{@code ALREADY_ASKED} (follow-up) - he was asked about this session within {@link #FOLLOW_UP_FRESH}
 *       ("one session can wake you more than once"): the same rule.</li>
 * </ul>
 * The two "already" facts were read by the model from the conversation; they are recorded here when such a message is
 * handed over ({@link #sent}): {@code quality_time.morning_reminded_on} and {@code quality_time.follow_up_asked_at}
 * (V49). D-058: the morning's is claimed atomically before it is sent ({@link #claimMorning}).
 */
@Component
public class SessionTimerCard {

    private static final Logger log = LoggerFactory.getLogger(SessionTimerCard.class);

    public static final String NO_VALID_SESSION = "NO_VALID_SESSION";
    public static final String ALREADY_REMINDED_TODAY = "ALREADY_REMINDED_TODAY";
    public static final String NOT_JUST_ENDED = "NOT_JUST_ENDED";
    public static final String ALREADY_ASKED = "ALREADY_ASKED";
    /** The follow-up rule's "about 180 minutes" and "the last three hours". */
    static final Duration FOLLOW_UP_FRESH = Duration.ofHours(3);

    /**
     * @param text           the message without the identity line (as the callback's plan), or null - nothing is sent
     * @param suppressReason why nothing is sent (null when it is)
     * @param sessions       the sessions the message names
     */
    public record Card(String text, String suppressReason, List<UUID> sessions) {
        public boolean goes() {
            return text != null;
        }

        static Card skip(String reason) {
            return new Card(null, reason, List.of());
        }
    }

    private final ScheduledReplies replies;
    private final WeeklyGoalService goals;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SessionTimerCard(ScheduledReplies replies, WeeklyGoalService goals, JdbcTemplate jdbc, Clock clock) {
        this.replies = replies;
        this.goals = goals;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The card of a session timer state at this moment; a state that is no session timer is refused. */
    @Transactional(readOnly = true)
    public Card build(Father father, String timerState) {
        if (!ScheduledReplies.isSessionTimer(timerState)) {
            throw new IllegalArgumentException("timer must be SESSION_MORNING_REMINDER, SESSION_REMINDER_1H or SESSION_FOLLOW_UP");
        }
        Instant now = clock.instant();
        ScheduledReplies.Planned planned = replies.plan(father, timerState, now).orElseThrow();
        if (planned.text() == null) {
            return Card.skip(NO_VALID_SESSION);
        }
        switch (timerState) {
            case ScheduledReplies.MORNING -> {
                LocalDate today = now.atZone(goals.zoneFor(father)).toLocalDate();
                boolean allReminded = planned.sessions().stream().allMatch(id -> today.equals(morningRemindedOn(id)));
                if (allReminded) {
                    return Card.skip(ALREADY_REMINDED_TODAY);
                }
            }
            case ScheduledReplies.FOLLOW_UP -> {
                UUID session = planned.sessions().get(0);
                Map<String, Object> row = jdbc.queryForMap(
                        "SELECT scheduled_end, follow_up_asked_at FROM quality_time WHERE id = ?", session);
                Instant end = instant(row.get("scheduled_end"));
                Instant asked = instant(row.get("follow_up_asked_at"));
                if (end.isBefore(now.minus(FOLLOW_UP_FRESH))) {
                    return Card.skip(NOT_JUST_ENDED);
                }
                if (asked != null && asked.isAfter(now.minus(FOLLOW_UP_FRESH))) {
                    return Card.skip(ALREADY_ASKED);
                }
            }
            default -> {
                // the hour-before: only the plan decides (its wish and its other rules stay the model's)
            }
        }
        return new Card(planned.text(), null, planned.sessions());
    }

    /** D-058: the sessions a morning reminder claimed for today, with what they held before (to give the claim back). */
    public record MorningClaim(long fatherId, LocalDate day, Map<UUID, LocalDate> before) {
    }

    /**
     * D-058: the morning reminder claims today for the sessions it names, in one atomic update, before it is sent. Two
     * sessions on one day wake the morning twice and both tool-only turns can pass {@link #build} before either message
     * is handed over; the claim lets exactly one of them go. Empty: every session was already claimed today.
     */
    public Optional<MorningClaim> claimMorning(Father father, List<UUID> sessions) {
        LocalDate today = clock.instant().atZone(goals.zoneFor(father)).toLocalDate();
        Map<UUID, LocalDate> before = new java.util.LinkedHashMap<>();
        jdbc.query("""
                UPDATE quality_time q SET morning_reminded_on = ?
                FROM (SELECT id, morning_reminded_on AS before FROM quality_time WHERE id = ANY (?::uuid[]) AND father_id = ?) o
                WHERE q.id = o.id AND q.morning_reminded_on IS DISTINCT FROM ?
                RETURNING q.id, o.before""",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    java.sql.Date was = rs.getDate("before");
                    before.put(rs.getObject("id", UUID.class), was == null ? null : was.toLocalDate());
                },
                today, sessions.stream().map(UUID::toString).toArray(String[]::new), father.getId(), today);
        return before.isEmpty() ? Optional.empty() : Optional.of(new MorningClaim(father.getId(), today, before));
    }

    /** D-058: the claimed morning was provably not sent: the sessions hold what they held before, unless claimed again since. */
    public void releaseMorning(MorningClaim claim) {
        claim.before().forEach((id, was) -> jdbc.update(
                "UPDATE quality_time SET morning_reminded_on = ? WHERE id = ? AND father_id = ? AND morning_reminded_on = ?",
                was, id, claim.fatherId(), claim.day()));
    }

    /**
     * A session timer's message was handed over for delivery (the callback): the follow-up notes the session it named,
     * so the next timer of the same session sends nothing again (the morning claims its day before it is sent:
     * {@link #claimMorning}). Best effort: a failure here never stops a message.
     */
    public void sent(Father father, String timerState, List<UUID> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        try {
            Instant now = clock.instant();
            if (ScheduledReplies.FOLLOW_UP.equals(timerState)) {
                for (UUID id : sessions) {
                    jdbc.update("UPDATE quality_time SET follow_up_asked_at = ? WHERE id = ? AND father_id = ?",
                            Timestamp.from(now), id, father.getId());
                }
            }
        } catch (RuntimeException e) {
            log.atWarn().setMessage("session_timer_card.record_failed").addKeyValue("state", timerState)
                    .addKeyValue("error", e.getClass().getSimpleName()).log();
        }
    }

    private LocalDate morningRemindedOn(UUID session) {
        Optional<java.sql.Date> on = jdbc.query("SELECT morning_reminded_on FROM quality_time WHERE id = ?",
                rs -> rs.next() ? Optional.ofNullable(rs.getDate(1)) : Optional.empty(), session);
        return on == null ? null : on.map(java.sql.Date::toLocalDate).orElse(null);
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof Instant i) {
            return i;
        }
        throw new IllegalStateException("unexpected timestamp type " + value.getClass());
    }
}
