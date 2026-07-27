package com.dadcoach.ai.decision;

import com.dadcoach.ai.output.ActionRecommendation.ActionType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks action history per father for Decision Engine gap enforcement
 * and action frequency management.
 *
 * <p>Each father has a list of recorded actions with timestamps.
 * The Decision Engine uses this to:
 * <ul>
 *   <li>Enforce the 4-hour gap between proactive outbound messages</li>
 *   <li>Track when the last action of each type occurred</li>
 *   <li>Count outbound messages per day</li>
 * </ul>
 *
 * <p>This is an in-memory tracker. In production, action history would be
 * persisted to the database and loaded on demand.
 */
public class ActionHistory {

    private final Map<UUID, List<ActionRecord>> historyByFather = new ConcurrentHashMap<>();

    /**
     * Records an action taken for a father.
     *
     * @param fatherId   the father's unique ID
     * @param actionType the action type that was recommended/executed
     * @param timestamp  when the action was taken
     * @param proactive  whether this was a proactive outbound (true) or response to inbound (false)
     */
    public void recordAction(UUID fatherId, ActionType actionType, Instant timestamp, boolean proactive) {
        historyByFather
            .computeIfAbsent(fatherId, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new ActionRecord(actionType, timestamp, proactive));
    }

    /**
     * Returns the timestamp of the last proactive outbound message for a father.
     *
     * @param fatherId the father's ID
     * @return the timestamp of the last proactive message, or null if none
     */
    public Instant getLastProactiveMessage(UUID fatherId) {
        List<ActionRecord> records = historyByFather.get(fatherId);
        if (records == null || records.isEmpty()) {
            return null;
        }
        return records.stream()
            .filter(ActionRecord::proactive)
            .map(ActionRecord::timestamp)
            .max(Instant::compareTo)
            .orElse(null);
    }

    /**
     * Checks if the 4-hour gap requirement is violated for proactive messages.
     *
     * @param fatherId the father's ID
     * @param now      the current time
     * @return true if a proactive message was sent less than 4 hours ago
     */
    public boolean isProactiveGapViolated(UUID fatherId, Instant now) {
        Instant lastProactive = getLastProactiveMessage(fatherId);
        if (lastProactive == null) {
            return false;
        }
        Duration elapsed = Duration.between(lastProactive, now);
        return elapsed.toHours() < 4;
    }

    /**
     * Returns the timestamp of the last action of a specific type for a father.
     *
     * @param fatherId   the father's ID
     * @param actionType the action type to look for
     * @return the timestamp of the last action of that type, or null if none
     */
    public Instant getLastActionOfType(UUID fatherId, ActionType actionType) {
        List<ActionRecord> records = historyByFather.get(fatherId);
        if (records == null || records.isEmpty()) {
            return null;
        }
        return records.stream()
            .filter(r -> r.actionType() == actionType)
            .map(ActionRecord::timestamp)
            .max(Instant::compareTo)
            .orElse(null);
    }

    /**
     * Returns all action records for a father (unmodifiable view).
     *
     * @param fatherId the father's ID
     * @return the list of action records, or empty list if none
     */
    public List<ActionRecord> getHistory(UUID fatherId) {
        List<ActionRecord> records = historyByFather.get(fatherId);
        if (records == null) {
            return List.of();
        }
        return List.copyOf(records);
    }

    /**
     * Clears all history (mainly for testing).
     */
    public void clear() {
        historyByFather.clear();
    }

    /**
     * A single recorded action for a father.
     *
     * @param actionType the type of action
     * @param timestamp  when the action occurred
     * @param proactive  whether this was a proactive outbound (true) or response to inbound (false)
     */
    public record ActionRecord(
        ActionType actionType,
        Instant timestamp,
        boolean proactive
    ) {
        public ActionRecord {
            if (actionType == null) {
                throw new IllegalArgumentException("actionType must not be null");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp must not be null");
            }
        }
    }
}
