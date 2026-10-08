package com.dadcoach.channel.template;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dad Coach's WhatsApp template catalog, in code (the Big Boss D-167 pattern): what the owner submits to
 * Meta is exactly what the code sends, and the admin's templates screen, its "mark approved" and the tests
 * all read this list.
 *
 * <p>A message outside the 24-hour window goes out in its own template when there is one for it and it is
 * approved ({@link TemplateRegistry}): the three session timers (the values come from the session, not
 * from the AI's text). Every other proactive message — the daily check's items, the sign-in link — and any
 * of these whose own template is not approved yet goes out in the general
 * {@code dad_coach_update_he}, with the whole message flattened into its {{1}}
 * ({@link com.dadcoach.channel.delivery.ProactiveSender#asTemplateParameter}).</p>
 *
 * <p>Every body opens with the identity line the free-form messages open with ("❤️ דאד קואץ׳:"). Quick-reply
 * buttons carry the same titles as the in-window buttons; their payloads ({@code dc:...}) are sent with each
 * message, so a tap is handled exactly like a tap inside the window.</p>
 */
public final class WhatsAppTemplateCatalog {

    public static final String IDENTITY = "❤️ דאד קואץ׳:\n";

    public static final String UPDATE_HE = "dad_coach_update_he";
    public static final String SESSION_MORNING_HE = "dad_coach_session_morning_he";
    public static final String SESSION_HOUR_BEFORE_HE = "dad_coach_session_hour_before_he";
    public static final String SESSION_FOLLOW_UP_HE = "dad_coach_session_follow_up_he";

    /** The general template's body: kept as a constant, the admin tests compare against it. */
    public static final String UPDATE_HE_BODY =
            IDENTITY + "{{1}}\n\nעדכון על זמן האיכות שלך עם הילדים. אפשר לענות כאן בכל רגע.";

    /**
     * @param purpose what it is, in Hebrew (the admin screen)
     * @param examples one example per variable, in order: what Meta's reviewers see
     * @param quickReplies the quick-reply button titles, in order (plain text, up to 25 characters)
     */
    public record Entry(String name, String category, String purpose, String body, List<String> examples,
                        List<String> quickReplies) {

        public String language() {
            return "he";
        }

        public int maxVariables() {
            return examples.size();
        }

        /** The body with these values in place, {{1}} first. */
        public String render(List<String> values) {
            String out = body;
            for (int i = 0; i < values.size(); i++) {
                out = out.replace("{{" + (i + 1) + "}}", values.get(i));
            }
            return out;
        }

        /** The message as the father reads it, with the examples in place. */
        public String sample() {
            return render(examples);
        }
    }

    public static final List<Entry> ALL = List.of(
            new Entry(UPDATE_HE, "UTILITY",
                    "התבנית הכללית: כל הודעה יזומה שאין לה תבנית משלה (בדיקת הבוקר, קישור לדף), וגיבוי לשלוש של המפגש",
                    UPDATE_HE_BODY,
                    List.of("עוד שעה הזמן שלך ושל מאיה 🙂 יש כבר רעיון מה תעשו?"), List.of()),
            new Entry(SESSION_MORNING_HE, "UTILITY",
                    "תזכורת בבוקר של יום המפגש",
                    IDENTITY + "*היום ב-{{1}}* זה הזמן שלך ושל {{2}} 🙂",
                    List.of("17:00", "מאיה"), List.of()),
            new Entry(SESSION_HOUR_BEFORE_HE, "UTILITY",
                    "תזכורת כשעה לפני המפגש, עם כפתור לרעיונות",
                    IDENTITY + "עוד שעה הזמן שלך ושל {{1}} 🙂\nיש כבר רעיון מה תעשו?",
                    List.of("מאיה"), List.of("רוצה רעיונות")),
            new Entry(SESSION_FOLLOW_UP_HE, "UTILITY",
                    "אחרי המפגש: איך היה, עם כפתורי היה / לא יצא",
                    IDENTITY + "נו, איך היה לכם עם {{1}}?",
                    List.of("מאיה"), List.of("היה מעולה", "לא יצא")));

    public static Optional<Entry> named(String name) {
        return ALL.stream().filter(e -> e.name().equals(name)).findFirst();
    }

    public static Entry require(String name) {
        return named(name).orElseThrow(() -> new IllegalArgumentException("no template " + name));
    }

    /** The values as the {"1": .., "2": ..} map a template send carries. */
    public static Map<String, String> parameters(List<String> values) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < values.size(); i++) {
            map.put(String.valueOf(i + 1), values.get(i));
        }
        return map;
    }

    private WhatsAppTemplateCatalog() {}
}
