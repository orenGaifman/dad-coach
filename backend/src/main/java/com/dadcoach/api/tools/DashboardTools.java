package com.dadcoach.api.tools;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.auth.LoginLinkService;
import com.dadcoach.auth.LoginLinkService.SendOutcome;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The father's dashboard. */
public final class DashboardTools {

    /** The whole reply when he only asked for his page: the button message says it all, so the product drops this line
     *  when the button went out in the same turn (owner, 2026-10-08: one message, not a card and a "sent you" line). */
    public static final String SENT_REPLY = "שלחתי לך כפתור לדף שלך, בהודעה נפרדת.";
    static final String SENT_NOTE = "The button to his page went out to him as its own WhatsApp message. When he only asked for "
            + "his page, your reply is exactly the reply line above (it is not shown when the button arrived). When the "
            + "button goes with something else you tell him (where to fix a child, how to connect his calendar), say that "
            + "in one short line. Never write a link or an address yourself.";
    /** His button from a few minutes ago is right above: nothing new goes out, and a question about it gets an answer. */
    public static final String ON_SCREEN_REPLY = "הכפתור בהודעה למעלה: לחיצה עליו פותחת את הדף שלך, עם השבוע, הילדים וההתקדמות.";
    static final String ON_SCREEN_NOTE = "His button to his page went out a few minutes ago and keeps working, so no new one "
            + "was sent. Reply with the reply line above. If he asked what the button is, the reply line is the answer. "
            + "Never write a link or an address yourself.";
    static final String RATE_LIMITED_NOTE = "A button to his page was already sent to him a few minutes ago, so no new one "
            + "went out. Tell him in one short line to tap the button in that recent message - it keeps working. "
            + "Never write a link or an address yourself.";
    static final String FAILED_NOTE = "The button could not be sent right now. Tell him in one short line, and that he can "
            + "ask again in a few minutes. Never write a link or an address yourself.";

    private DashboardTools() {
    }

    /**
     * {@code dad_dashboard_link} (D-027): sends the father - only the owner of this WhatsApp number - a button to his
     * Dad Coach page, as its own WhatsApp message. The link behind it is reusable for a year; the token never appears
     * in the result, so it never passes through the platform, the model or the conversation. A team member who is
     * also this father gets one link for both (his page first, the admin from the account menu).
     */
    @Component
    public static class DashboardLink implements ToolHandler {
        private final LoginLinkService links;

        public DashboardLink(LoginLinkService links) {
            this.links = links;
        }

        public String toolKey() { return "dad_dashboard_link"; }
        public boolean sideEffecting() { return true; }

        public Map<String, Object> handle(ToolActor actor, ToolParams p) {
            actor.requireFather();
            SendOutcome outcome = links.sendTo(actor.phone());
            if (outcome == SendOutcome.NOT_ALLOWED) {
                throw new ApiException(HttpStatus.FORBIDDEN, "DASHBOARD_NOT_AVAILABLE",
                        "His Dad Coach page is not available to him right now - say so kindly in one line, no link");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sent", outcome == SendOutcome.SENT);
            data.put("delivery", outcome.name());
            if (outcome == SendOutcome.SENT) {
                data.put("reply", SENT_REPLY);
            } else if (outcome == SendOutcome.ALREADY_SENT) {
                data.put("reply", ON_SCREEN_REPLY);
            }
            data.put("note", switch (outcome) {
                case SENT -> SENT_NOTE;
                case ALREADY_SENT -> ON_SCREEN_NOTE;
                case RATE_LIMITED -> RATE_LIMITED_NOTE;
                default -> FAILED_NOTE;
            });
            return data;
        }
    }
}
