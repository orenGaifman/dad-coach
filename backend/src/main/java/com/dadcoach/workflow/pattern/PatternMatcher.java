package com.dadcoach.workflow.pattern;

import java.util.List;
import java.util.Optional;

/**
 * Interface for matching user messages against state-specific patterns.
 * 
 * <p>The PatternMatcher is responsible for evaluating user input against a list of
 * expected patterns for the current workflow state. Patterns are evaluated in order;
 * the first match wins.</p>
 * 
 * <p>This enables deterministic message processing without AI interpretation, as specified
 * by the architecture decision AD-6: Pattern Matching Over NLU.</p>
 * 
 * <p>Implements Requirement 11.3 - State-specific message patterns using regex/keyword matching.</p>
 * 
 * @see PatternResult
 * @see StatePattern
 */
public interface PatternMatcher {

    /**
     * Match a user message against a list of state patterns.
     * 
     * <p>Patterns are evaluated in order; the first pattern that matches the input
     * wins and its associated action is returned in the result. If no pattern matches,
     * an empty Optional is returned.</p>
     * 
     * <p>The matching process:</p>
     * <ol>
     *   <li>Iterate through patterns in the order provided</li>
     *   <li>For each pattern, attempt to match against the input</li>
     *   <li>If matched, return a PatternResult with the pattern name, action, and any captured groups</li>
     *   <li>If no pattern matches, return Optional.empty()</li>
     * </ol>
     * 
     * @param input the user's message to match against patterns; must not be null
     * @param patterns the list of patterns to match against, evaluated in order; must not be null
     * @return an Optional containing the PatternResult if a pattern matched, or empty if no match
     * @throws NullPointerException if input or patterns is null
     */
    Optional<PatternResult> match(String input, List<StatePattern> patterns);
}
