package com.dadcoach.auth;

/**
 * What a sign-in link message says (D-027: "📊 הדף שלך בדאד קואץ׳" + "כניסה לדף שלי", a WhatsApp cta_url message - the
 * reusable link sits behind the button, never in the text). Since D-053 (Unified Workflow 6.4) the platform's Send API
 * sends it ({@link LoginLinkPlatform}) for a father and a staff user alike: the button inside the 24-hour window, the
 * text with the link on its own line when Meta refuses the button, and outside the window the template the platform's
 * mapping picks for {@code LOGIN_LINK} ({@code dad_coach_update_he}, {{1}} = {@link #templateLine}).
 */
public final class LoginLinkDelivery {

    /** The identity line every message Dad Coach sends by itself opens with (the number is shared). */
    static final String IDENTITY = "❤️ דאד קואץ׳:\n";
    static final String FATHER_TEXT = IDENTITY + "📊 *הדף שלך בדאד קואץ׳*\nהשבוע, הילדים וההתקדמות, הכול בדף אחד.";
    static final String FATHER_LABEL = "כניסה לדף שלי";
    static final String STAFF_TEXT = IDENTITY + "📊 *ניהול דאד קואץ׳*\nהכניסה שלך לניהול.";
    static final String STAFF_LABEL = "כניסה לניהול";
    /** "הקישור", not "הכפתור": the same footer closes the text fallback, where there is no button (D-032). */
    static final String FOOTER = "הקישור אישי, לא להעביר הלאה";
    static final String TEMPLATE_LINE_PREFIX = "הקישור לדף שלך, אישי ולא להעביר הלאה: ";
    static final String STAFF_TEMPLATE_LINE_PREFIX = "הקישור לניהול דאד קואץ׳, אישי ולא להעביר הלאה: ";

    private LoginLinkDelivery() {
    }

    /** The card as the timeline records it: its text without the identity line, never the link. */
    public static String reportedText(boolean asTemplate) {
        return asTemplate ? TEMPLATE_LINE_PREFIX.strip().replaceAll(":$", "")
                : com.dadcoach.integration.platform.timeline.TimelineText.withoutIdentity(FATHER_TEXT);
    }

    public static String fatherLabel() {
        return FATHER_LABEL;
    }

    /** Outside the window: the general template takes one line of text (no line breaks), the link inside it. */
    static String templateLine(String url) {
        return TEMPLATE_LINE_PREFIX + url;
    }

    /** A staff user's line for the general template (D-053: a staff link outside the window now goes as a template). */
    static String staffTemplateLine(String url) {
        return STAFF_TEMPLATE_LINE_PREFIX + url;
    }
}
