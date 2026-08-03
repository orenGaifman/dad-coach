package com.dadcoach.workflow.pattern;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Defines a pattern to match against user messages within a specific workflow state.
 * 
 * <p>A StatePattern binds together:</p>
 * <ul>
 *   <li><b>patternName</b>: A string identifier for the pattern (e.g., "AFFIRMATIVE", "SLOT_NUMBER")</li>
 *   <li><b>pattern</b>: A compiled regex {@link Pattern} for matching user input</li>
 *   <li><b>action</b>: The {@link WorkflowAction} to execute when the pattern matches</li>
 * </ul>
 * 
 * <p>Patterns are evaluated in order by the PatternMatcher. The first matching pattern
 * determines the action to take. This supports both English and Hebrew patterns for
 * bilingual message handling.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * StatePattern.of(
 *     "AFFIRMATIVE",
 *     Pattern.compile("(?i)^(yes|ready|let's go|lets go|ok|okay|sure|start|begin).*"),
 *     WorkflowAction.TRANSITION_TO_SCHEDULE
 * )
 * }</pre>
 * 
 * <p>Implements Requirement 11.3 - Pattern-based message processing without AI interpretation.</p>
 * 
 * @param patternName the human-readable name identifying this pattern (e.g., "AFFIRMATIVE", "SLOT_NUMBER")
 * @param pattern     the compiled regex pattern for matching user input
 * @param action      the workflow action to execute when this pattern matches
 */
public record StatePattern(
        String patternName,
        Pattern pattern,
        WorkflowAction action
) {

    /**
     * Canonical constructor with validation.
     * 
     * @throws NullPointerException if any parameter is null
     */
    public StatePattern {
        Objects.requireNonNull(patternName, "patternName must not be null");
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(action, "action must not be null");
        
        if (patternName.isBlank()) {
            throw new IllegalArgumentException("patternName must not be blank");
        }
    }

    /**
     * Static factory method to create a StatePattern.
     * 
     * @param name    the pattern name identifier
     * @param pattern the compiled regex pattern
     * @param action  the action to take when matched
     * @return a new StatePattern instance
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if name is blank
     */
    public static StatePattern of(String name, Pattern pattern, WorkflowAction action) {
        return new StatePattern(name, pattern, action);
    }

    /**
     * Static factory method to create a StatePattern from a regex string.
     * 
     * <p>This is a convenience method that compiles the regex pattern internally.</p>
     * 
     * @param name   the pattern name identifier
     * @param regex  the regex pattern string to compile
     * @param action the action to take when matched
     * @return a new StatePattern instance
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if name is blank
     * @throws java.util.regex.PatternSyntaxException if regex is invalid
     */
    public static StatePattern of(String name, String regex, WorkflowAction action) {
        Objects.requireNonNull(regex, "regex must not be null");
        return new StatePattern(name, Pattern.compile(regex), action);
    }

    /**
     * Tests whether the given input matches this pattern.
     * 
     * @param input the user input to test
     * @return true if the input matches, false otherwise
     */
    public boolean matches(String input) {
        if (input == null) {
            return false;
        }
        return pattern.matcher(input).matches();
    }

    /**
     * Attempts to match the input and returns a PatternResult if successful.
     * 
     * @param input the user input to match
     * @return a PatternResult with match details, or PatternResult.NO_MATCH if no match
     */
    public PatternResult match(String input) {
        if (input == null) {
            return PatternResult.noMatch();
        }
        
        var matcher = pattern.matcher(input);
        if (!matcher.matches()) {
            return PatternResult.noMatch();
        }
        
        // Extract captured groups as a map
        // For unnamed groups, use "group1", "group2", etc. as keys
        // The first captured group is also available as "value" for convenience
        var capturedGroups = new java.util.HashMap<String, String>();
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String group = matcher.group(i);
            if (group != null) {
                capturedGroups.put("group" + i, group);
                // First captured group is also available as "value" for convenience
                if (i == 1) {
                    capturedGroups.put("value", group);
                }
            }
        }
        
        // Try to extract named groups if the pattern uses them
        // Named groups in Java regex: (?<name>pattern)
        try {
            var patternStr = pattern.pattern();
            var namedGroupPattern = java.util.regex.Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
            var namedGroupMatcher = namedGroupPattern.matcher(patternStr);
            while (namedGroupMatcher.find()) {
                String groupName = namedGroupMatcher.group(1);
                String groupValue = matcher.group(groupName);
                if (groupValue != null) {
                    capturedGroups.put(groupName, groupValue);
                }
            }
        } catch (IllegalArgumentException e) {
            // Named group not found, ignore
        }
        
        return PatternResult.of(patternName, action, capturedGroups);
    }

    @Override
    public String toString() {
        return String.format("StatePattern[name=%s, pattern=%s, action=%s]", 
                patternName, pattern.pattern(), action);
    }
}
