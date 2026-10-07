package com.dadcoach.whatsapp;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The coach writes to fathers in Hebrew only. Production (2026-10-07) and a replay of that turn showed the model
 * sometimes writing its own reasoning as the reply, in English ("Noted — is there something…", "Fine as is, no
 * change needed.", "Got it — זה בסדר"). The prompt rule makes it rare; this is the last line before WhatsApp:
 * <ul>
 *   <li>a reply with no Hebrew letter and some English text is never sent ({@link Optional#empty()});</li>
 *   <li>an English note in front of the Hebrew message ("Got it — ", "No tool call needed - just clarifying. ") is
 *       cut off. Only a Latin run ending in punctuation counts as a note, so "Google Calendar מחובר" stays.</li>
 * </ul>
 * The identity line ("❤️ דאד קואץ׳:") that the platform puts on top is kept and does not count as Hebrew text.
 */
public final class ReplyLanguageGuard {

    private static final Pattern HEBREW = Pattern.compile("[\\u05D0-\\u05EA]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern IDENTITY_LINE = Pattern.compile("\\A([^\\n]*דאד קואץ׳:[ \\t]*\\n)");
    /** Links, [[SUPPRESS_RESPONSE]]-style markers and {{placeholders}} are not language. */
    private static final Pattern NOT_LANGUAGE = Pattern.compile("https?://\\S+|\\[\\[[^]]*]]|\\{\\{[^}]*}}");
    private static final Pattern LEADING_NOTE = Pattern.compile(
            "\\A\\s*[A-Za-z][A-Za-z0-9 ,'’()=_/-]*[.!:;—–-](?:\\s*[A-Za-z][A-Za-z0-9 ,'’()=_/-]*[.!:;—–-])*\\s*");
    private static final int MIN_LATIN_TO_BLOCK = 6;

    private ReplyLanguageGuard() {}

    /** The reply to send, or empty when it must not be sent. */
    public static Optional<String> clean(String reply) {
        if (reply == null) {
            return Optional.empty();
        }
        String identity = "";
        String body = reply;
        Matcher id = IDENTITY_LINE.matcher(reply);
        if (id.find()) {
            identity = id.group(1);
            body = reply.substring(identity.length());
        }
        String language = NOT_LANGUAGE.matcher(body).replaceAll(" ");
        if (!HEBREW.matcher(language).find()) {
            return count(LATIN, language) >= MIN_LATIN_TO_BLOCK ? Optional.empty() : Optional.of(reply);
        }
        Matcher note = LEADING_NOTE.matcher(body);
        if (note.find() && count(LATIN, note.group()) >= 2 && HEBREW.matcher(body.substring(note.end())).find()) {
            body = body.substring(note.end());
        }
        return Optional.of(identity + body);
    }

    /** True when {@link #clean} changed or blocked the reply - for logging. */
    public static boolean changes(String reply) {
        return clean(reply).map(c -> !c.equals(reply)).orElse(true);
    }

    private static int count(Pattern p, String s) {
        int n = 0;
        Matcher m = p.matcher(s);
        while (m.find()) {
            n++;
        }
        return n;
    }
}
