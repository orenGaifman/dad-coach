package com.dadcoach.ai.prompt;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable value object representing a versioned prompt template.
 * Supports Mustache-style placeholders (e.g., {{phase}}, {{child_name}}).
 *
 * <p>Once created, a template's content cannot be modified —
 * corrections require a new version per Requirement 8 criteria 4.
 */
public record PromptTemplate(
    PromptType promptType,
    PromptVersion version,
    String content,
    boolean isActive,
    String abTestGroup,
    Instant createdAt
) {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    public PromptTemplate {
        Objects.requireNonNull(promptType, "promptType must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("Template content must not be blank");
        }
        // abTestGroup can be null (no A/B test), "A", or "B"
        if (abTestGroup != null && !abTestGroup.equals("A") && !abTestGroup.equals("B")) {
            throw new IllegalArgumentException("abTestGroup must be null, \"A\", or \"B\"");
        }
    }

    /**
     * Resolve all Mustache-style placeholders in the template with provided values.
     * Unresolved placeholders remain as-is if not present in the parameters map.
     *
     * @param parameters map of placeholder name → replacement value
     * @return the resolved content string
     */
    public String resolve(Map<String, String> parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = parameters.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * @return a string identifier combining type, version, and group (e.g., "SYSTEM-1.0.0-A")
     */
    public String identifier() {
        String groupSuffix = abTestGroup != null ? "-" + abTestGroup : "";
        return "%s-%s%s".formatted(promptType, version, groupSuffix);
    }
}
