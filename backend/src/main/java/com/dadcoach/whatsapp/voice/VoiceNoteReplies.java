package com.dadcoach.whatsapp.voice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** D-029: what the father reads about a voice note - Hebrew, to him (masculine singular), signed by the coach. */
public final class VoiceNoteReplies {

    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    static final int HEARD_MAX = 200;
    private static final Pattern IDENTITY_LINE = Pattern.compile("\\A[^\\n]*דאד קואץ׳:[ \\t]*\\n");

    /**
     * What the coach reads in front of the words: they were spoken and written down by a machine. Hebrew, like
     * everything the coach answers (D-024) - an English note invites an English reply.
     */
    public static final String TURN_NOTE = "[הודעה קולית, תומללה אוטומטית - שמות ומספרים עלולים להישמע לא נכון]\n";

    private VoiceNoteReplies() {
    }

    /** Why a voice note was not heard, when voice notes are on. Never an AI turn. */
    public static String notHeard(VoiceNotes.Outcome outcome) {
        return IDENTITY + switch (outcome) {
            case VoiceNotes.Outcome.TooLong t -> "🎙️ ההקלטה ארוכה מדי בשבילי - שלח הקלטה קצרה יותר, או כתוב לי במילים 🙏";
            case VoiceNotes.Outcome.Silent s -> "🎙️ לא שמעתי מילים בהקלטה - נסה שוב, או כתוב לי במילים 🙏";
            default -> "🎙️ לא הצלחתי לשמוע את ההקלטה - אפשר לכתוב לי במילים? 🙏";
        };
    }

    /**
     * The reply to a voice note opens with what the coach heard, under the identity line, so a misheard word shows
     * at once. {@code heard} null leaves the reply as it is.
     */
    public static String withHeard(String reply, String heard) {
        if (reply == null || reply.isBlank() || heard == null || heard.isBlank()) {
            return reply;
        }
        String words = heard.strip().replaceAll("\\s+", " ");
        if (words.length() > HEARD_MAX) {
            words = words.substring(0, HEARD_MAX - 1).strip() + "…";
        }
        String line = "🎙️ שמעתי: \"" + words + "\"\n\n";
        Matcher identity = IDENTITY_LINE.matcher(reply);
        return identity.find() ? reply.substring(0, identity.end()) + line + reply.substring(identity.end()) : line + reply;
    }
}
