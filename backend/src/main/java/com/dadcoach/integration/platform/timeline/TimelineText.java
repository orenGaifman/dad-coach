package com.dadcoach.integration.platform.timeline;

import com.dadcoach.channel.dto.MessageType;
import com.dadcoach.channel.dto.OutboundMessageDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * D-039: the text rules of the timeline reports. A report carries what was sent without the identity line ("❤️ דאד
 * קואץ׳:" - the platform strips it from history anyway, P-B4). A reply is "as the platform wrote it" when it differs
 * only by the identity line and a voice note's "🎙️ שמעתי: ..." line.
 */
public final class TimelineText {

    /** The identity line a message opens with (any opening emoji, as the template bodies and fixed lines write it). */
    private static final Pattern IDENTITY_LINE = Pattern.compile("\\A\\s*[^\\n]*דאד קואץ׳:[ \\t]*(\\r?\\n|\\z)");
    /** VoiceNoteReplies.withHeard's line (always followed by an empty line). */
    private static final Pattern HEARD_LINE = Pattern.compile("\\A🎙️ שמעתי: \"[^\\n]*\"\\n\\n");

    private TimelineText() {
    }

    /** What was sent, without the identity line. */
    public static String withoutIdentity(String text) {
        if (text == null) {
            return null;
        }
        return IDENTITY_LINE.matcher(text).replaceFirst("").strip();
    }

    /** True when {@code sent} is the platform's {@code draft} as written (identity and heard lines aside). */
    public static boolean sameAsDraft(String sent, String draft) {
        if (sent == null || draft == null) {
            return false;
        }
        return core(sent).equals(core(draft));
    }

    private static String core(String text) {
        String body = withoutIdentity(text);
        return HEARD_LINE.matcher(body).replaceFirst("").strip();
    }

    /** A father's message with no words, as the timeline shows it (the coach's own markers: "[photo] ", ...). */
    public static String marker(MessageType type) {
        if (type == null) {
            return "[message]";
        }
        return switch (type) {
            case AUDIO -> "[voice note]";
            case IMAGE -> "[photo]";
            case VIDEO -> "[video]";
            case DOCUMENT -> "[file]";
            case LOCATION -> "[location]";
            default -> "[" + type.name().toLowerCase(java.util.Locale.ROOT) + "]";
        };
    }

    /** The platform's messageType for a father's message ({@code /execute} uses text / button_reply the same way). */
    public static String messageType(MessageType type) {
        if (type == null) {
            return "text";
        }
        return type == MessageType.INTERACTIVE ? "button_reply" : type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Reply buttons as the timeline records them: id (the "dc:" payload) and title. */
    public static List<Map<String, Object>> buttons(List<OutboundMessageDto.ReplyButton> buttons) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (buttons == null) {
            return out;
        }
        for (OutboundMessageDto.ReplyButton b : buttons) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.id());
            m.put("title", b.title());
            out.add(m);
        }
        return out;
    }

    /** A link button as the timeline records it: its title only - the URL (a sign-in token) never leaves Dad Coach. */
    public static List<Map<String, Object>> linkButton(String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "url");
        m.put("title", title);
        return List.of(m);
    }

    /** {@code {name, params}} of a template send ({@code params} omitted when null). */
    public static Map<String, Object> template(String name, List<String> params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        if (params != null) {
            m.put("params", List.copyOf(params));
        }
        return m;
    }
}
