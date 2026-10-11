package com.dadcoach.channel.template;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The text values Dad Coach hands the platform for a template (Phase 6.4, D-053: {@code templateData.values} /
 * {@code templateData.named.text}); the platform's kind→template mapping decides which Meta template carries them.
 * Moved here unchanged from the retired {@code ProactiveSender}.
 */
public final class TemplateText {

    private TemplateText() {
    }

    /** A template value: WhatsApp rejects newlines, tabs and more than four spaces in a row. */
    public static String oneLine(String value) {
        return value.replaceAll("\\s*\\R\\s*", " ").replace('\t', ' ').replaceAll(" {4,}", "   ").strip();
    }

    /** The identity line a message opens with ("❤️ דאד קואץ׳:"); the template body carries its own. */
    private static final Pattern IDENTITY_LINE = Pattern.compile("\\A\\s*[^\\n]*דאד קואץ׳:[ \\t]*(\\r?\\n|\\z)");
    /** A line that already ends a sentence (punctuation, a closing quote or bracket, an emoji or other symbol). */
    private static final Pattern ENDED = Pattern.compile("[.?!:;,…)\"'\\p{So}\\p{Sk}\\x{FE0F}\\x{200D}]$");

    /**
     * The message as the one line the general template takes as its {{1}} (D-032). WhatsApp rejects template text
     * parameters containing newlines, tabs or more than four consecutive spaces. The template body is
     * "❤️ דאד קואץ׳:" / {{1}} / a fixed closing line, so the message's own identity line is dropped (it would show
     * twice); each line that does not end a sentence gets a period, so the lines still read as sentences; list
     * items keep their "•".
     */
    public static String asTemplateParameter(String content) {
        String body = IDENTITY_LINE.matcher(content).replaceFirst("");
        List<String> lines = body.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        StringBuilder flat = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replace('\t', ' ');
            flat.append(line);
            if (i < lines.size() - 1) {
                boolean listFollows = lines.get(i + 1).startsWith("•");
                if (!listFollows && !ENDED.matcher(line).find()) {
                    flat.append('.');
                }
                flat.append(' ');
            }
        }
        return flat.toString().replaceAll(" {4,}", "   ").trim();
    }
}
