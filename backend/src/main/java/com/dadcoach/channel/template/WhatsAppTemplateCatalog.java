package com.dadcoach.channel.template;

import java.util.List;
import java.util.Optional;

/**
 * Dad Coach's WhatsApp template catalog, in code (the Big Boss D-167 pattern): what the owner submits to
 * Meta is exactly what the code sends. Today that is one general template, {@code dad_coach_update_he}
 * (D-032): every proactive message outside the 24-hour window goes out as its {{1}}, flattened to one
 * line by {@link com.dadcoach.channel.delivery.ProactiveSender#asTemplateParameter}. The body opens with
 * the same identity line the free-form messages open with ("❤️ דאד קואץ׳:"), which is why the sender
 * drops that line from the value — it is already in the template. The per-message drafts in
 * marketing/whatsapp-templates.md are not here because no code sends them yet.
 */
public final class WhatsAppTemplateCatalog {

    public static final String UPDATE_HE = "dad_coach_update_he";

    /** The body as submitted to Meta: identity line, the coach's line as {{1}}, a fixed closing line. */
    public static final String UPDATE_HE_BODY =
            "❤️ דאד קואץ׳:\n{{1}}\n\nעדכון על זמן האיכות שלך עם הילדים. אפשר לענות כאן בכל רגע.";

    /** The {{1}} example Meta reviewers see: a real reminder, one line, as the sender builds it. */
    public static final String UPDATE_HE_EXAMPLE = "עוד שעה הזמן שלך ושל מאיה 🙂 יש כבר רעיון מה תעשו?";

    public record Entry(String name, String language, String category, String body, int maxVariables,
                        String example) {

        /** The message as the father reads it, with the example in place. */
        public String sample() {
            return body.replace("{{1}}", example);
        }
    }

    public static final List<Entry> ALL =
            List.of(new Entry(UPDATE_HE, "he", "UTILITY", UPDATE_HE_BODY, 1, UPDATE_HE_EXAMPLE));

    public static Optional<Entry> named(String name) {
        return ALL.stream().filter(e -> e.name().equals(name)).findFirst();
    }

    private WhatsAppTemplateCatalog() {}
}
