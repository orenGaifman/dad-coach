package com.dadcoach.ai.prompt;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a semantic version (major.minor.patch) for prompt templates.
 * Immutable value object ensuring valid version format.
 */
public record PromptVersion(int major, int minor, int patch) implements Comparable<PromptVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    public PromptVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components must be non-negative");
        }
    }

    /**
     * Parse a version string like "1.2.3" into a PromptVersion.
     */
    public static PromptVersion parse(String versionString) {
        Objects.requireNonNull(versionString, "Version string must not be null");
        var matcher = VERSION_PATTERN.matcher(versionString.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid version format: '%s'. Expected major.minor.patch (e.g., '1.0.0')".formatted(versionString));
        }
        return new PromptVersion(
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))
        );
    }

    @Override
    public int compareTo(PromptVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return "%d.%d.%d".formatted(major, minor, patch);
    }
}
