package com.dadcoach.onboarding.localization;

import com.dadcoach.onboarding.session.WizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of LocalizationService using Spring MessageSource.
 * Supports named placeholder interpolation ({father_name} style) converted to positional args,
 * and falls back to English with a warning log when a key is missing in the target language.
 */
@Service
public class LocalizationServiceImpl implements LocalizationService {

    private static final Logger log = LoggerFactory.getLogger(LocalizationServiceImpl.class);

    private static final String DEFAULT_LANGUAGE = "en";
    private static final Pattern NAMED_PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    private static final Map<String, String> DATE_FORMATS = Map.of(
        "he", "dd/MM/yyyy",
        "en", "MM/dd/yyyy"
    );

    private static final Map<String, String> TIME_FORMATS = Map.of(
        "he", "HH:mm",
        "en", "h:mm a"
    );

    /**
     * Prefixes for step-related message keys. Each wizard step has keys under
     * wizard.{step_name_lowercase}.* in the resource bundles.
     */
    private static final List<String> STEP_MESSAGE_SUFFIXES = List.of(
        "title", "description", "instructions", "submit_button", "skip_button", "back_button"
    );

    private final MessageSource messageSource;

    public LocalizationServiceImpl(@Qualifier("onboardingMessageSource") MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public String getMessage(String key, String language, Object... args) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String lang = normalizeLanguage(language);
        Locale locale = Locale.forLanguageTag(lang);

        // First try to resolve the raw template to check for named placeholders
        String template = resolveTemplate(key, locale, lang);
        if (template == null) {
            return key; // Return the key itself if not found anywhere
        }

        // Interpolate named placeholders if args are provided as key-value pairs
        return interpolate(template, args);
    }

    @Override
    public Map<String, String> getStepMessages(WizardStep step, String language) {
        if (step == null) {
            return Collections.emptyMap();
        }

        String lang = normalizeLanguage(language);
        String stepPrefix = "wizard." + step.name().toLowerCase();
        Map<String, String> messages = new LinkedHashMap<>();

        for (String suffix : STEP_MESSAGE_SUFFIXES) {
            String key = stepPrefix + "." + suffix;
            String resolved = getMessage(key, lang);
            if (!resolved.equals(key)) { // Only include if actually resolved
                messages.put(suffix, resolved);
            }
        }

        return messages;
    }

    @Override
    public TextDirection getTextDirection(String language) {
        return TextDirection.forLanguage(language);
    }

    @Override
    public String getDateFormat(String language) {
        String lang = normalizeLanguage(language);
        return DATE_FORMATS.getOrDefault(lang, DATE_FORMATS.get(DEFAULT_LANGUAGE));
    }

    @Override
    public String getTimeFormat(String language) {
        String lang = normalizeLanguage(language);
        return TIME_FORMATS.getOrDefault(lang, TIME_FORMATS.get(DEFAULT_LANGUAGE));
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    /**
     * Resolves a message template from the message source with fallback to English.
     * Returns null if not found in any language.
     */
    private String resolveTemplate(String key, Locale targetLocale, String language) {
        try {
            return messageSource.getMessage(key, null, targetLocale);
        } catch (NoSuchMessageException e) {
            // Fallback to English with warning log
            if (!DEFAULT_LANGUAGE.equals(language)) {
                log.warn("Message key '{}' not found for language '{}', falling back to English", key, language);
                try {
                    return messageSource.getMessage(key, null, Locale.ENGLISH);
                } catch (NoSuchMessageException fallbackEx) {
                    log.warn("Message key '{}' not found in fallback language English either", key);
                    return null;
                }
            }
            return null;
        }
    }

    /**
     * Interpolates named placeholders in a message template.
     * Supports two usage patterns:
     * 1. Named placeholders with Map-like args: getMessage("key", "he", "father_name", "David")
     *    - Args come in pairs: name1, value1, name2, value2, ...
     * 2. Positional args: getMessage("key", "he", "David", "John")
     *    - Standard MessageFormat positional {0}, {1}, etc.
     */
    String interpolate(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }

        // Check if template contains named placeholders
        Matcher matcher = NAMED_PLACEHOLDER_PATTERN.matcher(template);
        if (matcher.find()) {
            // Try to interpret args as name-value pairs
            if (args.length >= 2 && args[0] instanceof String) {
                Map<String, Object> namedArgs = new LinkedHashMap<>();
                for (int i = 0; i + 1 < args.length; i += 2) {
                    if (args[i] instanceof String name) {
                        namedArgs.put(name, args[i + 1]);
                    }
                }
                if (!namedArgs.isEmpty()) {
                    return replaceNamedPlaceholders(template, namedArgs);
                }
            }
            // If args don't match name-value pattern, replace positionally
            return replaceNamedPlaceholdersPositional(template, args);
        }

        // No named placeholders — treat as positional {0}, {1}, etc.
        return replacePositionalPlaceholders(template, args);
    }

    private String replaceNamedPlaceholders(String template, Map<String, Object> namedArgs) {
        String result = template;
        for (Map.Entry<String, Object> entry : namedArgs.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private String replaceNamedPlaceholdersPositional(String template, Object[] args) {
        // Replace named placeholders with positional args in order of appearance
        Matcher matcher = NAMED_PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        while (matcher.find()) {
            String replacement = argIndex < args.length
                ? (args[argIndex] != null ? args[argIndex].toString() : "")
                : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            argIndex++;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String replacePositionalPlaceholders(String template, Object[] args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            String value = args[i] != null ? args[i].toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return language.toLowerCase().trim();
    }
}
