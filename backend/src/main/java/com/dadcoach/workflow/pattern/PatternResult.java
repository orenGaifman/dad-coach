package com.dadcoach.workflow.pattern;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of pattern matching against user input.
 * 
 * <p>This immutable record captures the outcome of the PatternMatcher evaluating
 * a user message against the expected patterns for the current workflow state.</p>
 * 
 * <p>The PatternResult contains:</p>
 * <ul>
 *   <li><b>patternName</b> - Identifies which pattern matched (e.g., "AFFIRMATIVE", "SLOT_NUMBER")</li>
 *   <li><b>matchedAction</b> - The {@link WorkflowAction} to take based on the match</li>
 *   <li><b>capturedGroups</b> - Named captured groups from regex matching</li>
 * </ul>
 * 
 * <p>Implements Requirement 11.3 - Pattern-based message processing without AI interpretation.</p>
 * 
 * @param patternName    The name of the matched pattern (e.g., "AFFIRMATIVE", "SLOT_NUMBER").
 *                       Null if no pattern matched.
 * @param matchedAction  The action to take based on the matched pattern.
 *                       Null if no pattern matched.
 * @param capturedGroups Named captured groups from regex matching (e.g., {"slotNumber": "2"}).
 *                       Empty map if no groups captured or no match. Keys are group names,
 *                       values are the captured text.
 */
public record PatternResult(
        String patternName,
        WorkflowAction matchedAction,
        Map<String, String> capturedGroups
) {

    /**
     * Represents a result where no pattern was matched.
     */
    public static final PatternResult NO_MATCH = new PatternResult(null, null, Collections.emptyMap());

    /**
     * Canonical constructor with validation and defensive copy.
     */
    public PatternResult {
        // Defensive copy of captured groups to ensure immutability
        capturedGroups = capturedGroups == null 
                ? Collections.emptyMap() 
                : Map.copyOf(capturedGroups);
    }

    /**
     * Creates a PatternResult for a successful match with named captured groups.
     * 
     * @param patternName    the name of the matched pattern
     * @param matchedAction  the action to take
     * @param capturedGroups the named captured regex groups
     * @return a new PatternResult instance
     * @throws NullPointerException if patternName or matchedAction is null
     */
    public static PatternResult of(String patternName, WorkflowAction matchedAction, Map<String, String> capturedGroups) {
        Objects.requireNonNull(patternName, "patternName must not be null");
        Objects.requireNonNull(matchedAction, "matchedAction must not be null");
        return new PatternResult(patternName, matchedAction, capturedGroups);
    }

    /**
     * Creates a PatternResult for a successful match without captured groups.
     * 
     * @param patternName   the name of the matched pattern
     * @param matchedAction the action to take
     * @return a new PatternResult instance
     * @throws NullPointerException if patternName or matchedAction is null
     */
    public static PatternResult of(String patternName, WorkflowAction matchedAction) {
        return of(patternName, matchedAction, Collections.emptyMap());
    }

    /**
     * Returns a result indicating no pattern matched.
     * 
     * @return the singleton NO_MATCH instance
     */
    public static PatternResult noMatch() {
        return NO_MATCH;
    }

    /**
     * Checks if a pattern was matched.
     * 
     * @return true if a pattern was matched, false otherwise
     */
    public boolean isMatched() {
        return patternName != null && matchedAction != null;
    }

    /**
     * Returns the matched action as an Optional.
     * 
     * @return Optional containing the action if matched, empty otherwise
     */
    public Optional<WorkflowAction> getActionOptional() {
        return Optional.ofNullable(matchedAction);
    }

    /**
     * Returns the captured group with the specified name, if present.
     * 
     * @param groupName the name of the captured group
     * @return Optional containing the group value, empty if not found
     */
    public Optional<String> getCapturedGroup(String groupName) {
        return Optional.ofNullable(capturedGroups.get(groupName));
    }

    /**
     * Returns the value of the "value" group, which is the default group name
     * for patterns that capture a single value (e.g., slot number).
     * 
     * @return Optional containing the captured value, empty if not found
     */
    public Optional<String> getCapturedValue() {
        return getCapturedGroup("value");
    }

    /**
     * Checks if there are any captured groups.
     * 
     * @return true if at least one group was captured
     */
    public boolean hasCapturedGroups() {
        return !capturedGroups.isEmpty();
    }

    /**
     * Checks if a specific captured group exists.
     * 
     * @param groupName the name of the captured group
     * @return true if the group exists
     */
    public boolean hasCapturedGroup(String groupName) {
        return capturedGroups.containsKey(groupName);
    }
}
