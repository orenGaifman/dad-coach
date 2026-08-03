package com.dadcoach.workflow.pattern;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of {@link PatternMatcher} using regex pattern matching.
 * 
 * <p>This implementation evaluates patterns in order; the first pattern that matches
 * the input wins. This allows for priority-based pattern matching where more specific
 * patterns can be listed before more general ones.</p>
 * 
 * <p>The implementation supports both English and Hebrew patterns by delegating to
 * {@link StatePattern#match(String)}, which handles regex matching and captured
 * group extraction.</p>
 * 
 * <p>Implements Requirement 11.3 - Pattern-based message processing without AI interpretation.</p>
 * 
 * <h3>Pattern Evaluation</h3>
 * <ol>
 *   <li>Patterns are evaluated in the order they appear in the list</li>
 *   <li>First match wins - no further patterns are evaluated after a match</li>
 *   <li>Captured groups from the regex are extracted and included in the result</li>
 * </ol>
 * 
 * <h3>Thread Safety</h3>
 * <p>This class is stateless and thread-safe. It can be safely used as a Spring
 * singleton component.</p>
 * 
 * @see PatternMatcher
 * @see StatePattern
 * @see PatternResult
 */
@Component
public class PatternMatcherImpl implements PatternMatcher {

    /**
     * Match a user message against a list of state patterns.
     * 
     * <p>Evaluates patterns in order; the first pattern that matches the input wins
     * and its associated action is returned in the result. If no pattern matches,
     * an empty Optional is returned.</p>
     * 
     * @param input    the user's message to match against patterns; must not be null
     * @param patterns the list of patterns to match against, evaluated in order; must not be null
     * @return an Optional containing the PatternResult if a pattern matched, or empty if no match
     * @throws NullPointerException if input or patterns is null
     */
    @Override
    public Optional<PatternResult> match(String input, List<StatePattern> patterns) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(patterns, "patterns must not be null");

        // Evaluate patterns in order - first match wins
        for (StatePattern pattern : patterns) {
            PatternResult result = pattern.match(input);
            if (result.isMatched()) {
                return Optional.of(result);
            }
        }

        // No pattern matched
        return Optional.empty();
    }
}
