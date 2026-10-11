package com.dadcoach.api.tools;

import com.dadcoach.integration.platform.WorkflowPlatformProperties;
import com.dadcoach.integration.platform.scheduled.SessionTimerCard;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The platform's session timer turns (D-049). */
public final class SessionTimerTools {

    /** No Dad Coach father for this number: the callback would refuse the message (UNKNOWN_RECIPIENT), so none is built. */
    public static final String UNKNOWN_RECIPIENT = "UNKNOWN_RECIPIENT";
    /** The guard note when DC_SESSION_TIMERS_GUARD is off: it proceeded without deciding anything. */
    public static final String GUARD_OFF = "OFF";

    private SessionTimerTools() {
    }

    /**
     * {@code dc_session_timer_card} (D-049, read-only; Unified Workflow Phase 5.3 slice D): never shown to the model.
     * <ul>
     *   <li>The tool-only {@code SESSION_MORNING_REMINDER} / {@code SESSION_FOLLOW_UP} turns: the platform sends
     *       {@code message.text} back through the scheduled callback as the turn's reply; {@code message: null} with a
     *       {@code suppressReason} ends the turn with nothing sent and no callback.</li>
     *   <li>The {@code SESSION_REMINDER_1H} AI turn's guard: {@code proceed} decides whether the model runs (false only
     *       when there is no session to remind about, and only with DC_SESSION_TIMERS_GUARD on).</li>
     * </ul>
     * {@code timer} is the state (static in the workflow); {@code firedAt} / {@code referenceId} are only logged - like
     * the callback, it decides at this moment, from Dad Coach's own clock.
     */
    @Component
    public static class Card implements ToolHandler {
        public static final String KEY = "dc_session_timer_card";
        private static final Logger log = LoggerFactory.getLogger(Card.class);

        private final SessionTimerCard cards;
        private final WorkflowPlatformProperties properties;

        public Card(SessionTimerCard cards, WorkflowPlatformProperties properties) {
            this.cards = cards;
            this.properties = properties;
        }

        public String toolKey() { return KEY; }
        public boolean sideEffecting() { return false; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            String timer = p.requiredStr("timer");
            SessionTimerCard.Card card = actor.father().map(f -> cards.build(f, timer))
                    .orElseGet(() -> {
                        if (!com.dadcoach.integration.platform.scheduled.ScheduledReplies.isSessionTimer(timer)) {
                            throw new IllegalArgumentException("timer must be SESSION_MORNING_REMINDER, SESSION_REMINDER_1H or SESSION_FOLLOW_UP");
                        }
                        return new SessionTimerCard.Card(null, UNKNOWN_RECIPIENT, java.util.List.of());
                    });
            boolean guardOn = properties.isSessionTimersGuard();
            boolean proceed = !guardOn || card.goes();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", card.goes() ? Map.of("text", card.text()) : null);
            data.put("suppressReason", card.suppressReason());
            data.put("proceed", proceed);
            data.put("timer", timer);
            if (!guardOn) {
                data.put("guard", GUARD_OFF);
            }
            log.atInfo().setMessage(card.goes() ? "session_timer_card.built" : "session_timer_card.suppressed")
                    .addKeyValue("fatherId", actor.father().map(f -> f.getId()).orElse(null))
                    .addKeyValue("timer", timer).addKeyValue("suppressReason", card.suppressReason())
                    .addKeyValue("proceed", proceed).addKeyValue("firedAt", p.str("firedAt"))
                    .addKeyValue("referenceId", p.str("referenceId")).log();
            return data;
        }
    }
}
