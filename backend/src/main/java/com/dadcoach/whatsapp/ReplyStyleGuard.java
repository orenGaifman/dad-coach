package com.dadcoach.whatsapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The message standard, enforced in code on every coach reply and scheduled message (D-036; findings D-9, D-10 of the
 * production review 2, 2026-10-08: 😊 in 26 of 40 replies, 👍 ✅ 🙏 🎮 🍳 🥞 🎲 🚲, three bubbles with two or more emoji,
 * and " - " joining two sentences in 25 of 40). The prompt says the same; this is the last line before WhatsApp:
 * <ul>
 *   <li>Dad Coach's vocabulary only - 🙂 opening/reminder, 🎉 booked, 💪 progress, 📊 his page, 🎙️ a voice note (owner,
 *       2026-10-08). Near ones are mapped to it (😊 → 🙂, 👍 ✅ 🔥 → 💪, 🥳 → 🎉), any other is dropped, and only the
 *       first one in the message stays;</li>
 *   <li>a line that was a list item behind an emoji ("🥞 בישול יחד") becomes a "•" item; "- " items become "•";</li>
 *   <li>" - " / " — " between words becomes ", " (after a sentence's end, a space) - never inside numbers or time
 *       ranges ("09:00-10:30", "2-3", "2 - 3" stay), never the Hebrew prefix dash ("ב-17:00");</li>
 *   <li>"**bold**" becomes WhatsApp's "*bold*".</li>
 * </ul>
 * The identity line ("❤️ דאד קואץ׳:") the platform puts on top is left as it is.
 */
public final class ReplyStyleGuard {

    public static final List<String> VOCABULARY = List.of("🙂", "🎉", "💪", "📊", "🎙️");

    private static final Map<String, String> NEAR = Map.ofEntries(
            Map.entry("😊", "🙂"), Map.entry("☺", "🙂"), Map.entry("😀", "🙂"), Map.entry("😃", "🙂"), Map.entry("😄", "🙂"),
            Map.entry("😁", "🙂"), Map.entry("😆", "🙂"), Map.entry("🤗", "🙂"), Map.entry("😉", "🙂"), Map.entry("😌", "🙂"),
            Map.entry("🥰", "🙂"), Map.entry("😍", "🙂"), Map.entry("🙃", "🙂"),
            Map.entry("👍", "💪"), Map.entry("👏", "💪"), Map.entry("🙌", "💪"), Map.entry("🔥", "💪"), Map.entry("✅", "💪"),
            Map.entry("✔", "💪"), Map.entry("💯", "💪"), Map.entry("🏆", "💪"), Map.entry("⭐", "💪"), Map.entry("🌟", "💪"),
            Map.entry("👌", "💪"), Map.entry("🤝", "💪"), Map.entry("🥋", "💪"),
            Map.entry("🥳", "🎉"), Map.entry("🎊", "🎉"),
            Map.entry("📈", "📊"), Map.entry("📋", "📊"),
            Map.entry("🎤", "🎙️"), Map.entry("🎙", "🎙️"));

    /** One emoji: a pictograph with its variation selector, skin tone and ZWJ parts. */
    private static final Pattern EMOJI = Pattern.compile(
            "\\p{IsExtended_Pictographic}(?:\\x{FE0F}|[\\x{1F3FB}-\\x{1F3FF}]|\\x{200D}\\p{IsExtended_Pictographic}\\x{FE0F}?)*");
    private static final Pattern IDENTITY_LINE = Pattern.compile("\\A([^\\n]*דאד קואץ׳:[ \\t]*\\n)");
    private static final Pattern DASH_BETWEEN = Pattern.compile("(?<=([\\p{L}\\p{N}\\p{So}.,!?:;)\"'*]))[ \\t]+[-–—][ \\t]+(?=(\\p{L}|\\*))");
    private static final Pattern HEBREW_DASH = Pattern.compile("(?<=[\\u05D0-\\u05EA])[—–](?=[\\u05D0-\\u05EA])");
    private static final Pattern DASH_ITEM = Pattern.compile("^[ \\t]*[-–—][ \\t]+");

    private ReplyStyleGuard() {
    }

    public static String clean(String reply) {
        if (reply == null || reply.isBlank()) {
            return reply;
        }
        String identity = "";
        String body = reply;
        Matcher id = IDENTITY_LINE.matcher(reply);
        if (id.find()) {
            identity = id.group(1);
            body = reply.substring(identity.length());
        }
        return identity + dashes(emoji(body.replace("**", "*")));
    }

    static String emoji(String body) {
        String[] lines = body.split("\n", -1);
        int itemLines = 0;
        for (String line : lines) {
            Matcher m = EMOJI.matcher(line.stripLeading());
            if (m.lookingAt() && !VOCABULARY.contains(mapped(m.group()))) {
                itemLines++;
            }
        }
        boolean kept = false;
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            boolean leadingDropped = false;
            Matcher m = EMOJI.matcher(line);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                sb.append(line, last, m.start());
                String to = mapped(m.group());
                if (VOCABULARY.contains(to) && !kept) {
                    sb.append(to);
                    kept = true;
                } else if (line.substring(0, m.start()).isBlank()) {
                    leadingDropped = true;
                }
                last = m.end();
            }
            sb.append(line.substring(last));
            String cleaned = sb.toString().replace("️", "").replaceAll("[ \\t]{2,}", " ")
                    .replaceAll("[ \\t]+([.,!?:;])", "$1").stripTrailing();
            if (leadingDropped) {
                cleaned = cleaned.stripLeading();
                if (itemLines >= 2 && !cleaned.isEmpty() && !cleaned.startsWith("•")) {
                    cleaned = "• " + cleaned;
                }
            }
            out.add(cleaned);
        }
        // a 🎙️ that was kept loses its variation selector above; put it back
        return String.join("\n", out).replace("🎙 ", "🎙️ ").replaceAll("🎙$", "🎙️");
    }

    private static String mapped(String emoji) {
        String bare = emoji.replace("️", "");
        if (bare.equals("🎙")) {
            return "🎙️";
        }
        if (VOCABULARY.contains(bare)) {
            return bare;
        }
        return NEAR.getOrDefault(bare, "");
    }

    static String dashes(String body) {
        List<String> out = new ArrayList<>();
        for (String line : body.split("\n", -1)) {
            String l = DASH_ITEM.matcher(line).replaceFirst("• ");
            l = HEBREW_DASH.matcher(l).replaceAll(", ");
            Matcher m = DASH_BETWEEN.matcher(l);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String before = m.group(1);
                String after = m.group(2);
                boolean numbers = Character.isDigit(before.charAt(0)) && Character.isDigit(after.charAt(0));
                boolean ended = ".!?:;,".contains(before) || Character.getType(before.codePointAt(0)) == Character.OTHER_SYMBOL;
                String to = numbers ? m.group() : ended ? " " : ", ";
                m.appendReplacement(sb, Matcher.quoteReplacement(to));
            }
            m.appendTail(sb);
            out.add(sb.toString());
        }
        return String.join("\n", out);
    }
}
