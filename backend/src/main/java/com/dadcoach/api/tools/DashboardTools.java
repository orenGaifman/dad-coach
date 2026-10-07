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

    static final String SENT_NOTE = "The button to his page went out to him as its own WhatsApp message. Say in one short "
            + "line that it is on its way (and that the same button keeps working whenever he wants to come back). "
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
            data.put("note", switch (outcome) {
                case SENT -> SENT_NOTE;
                case RATE_LIMITED -> RATE_LIMITED_NOTE;
                default -> FAILED_NOTE;
            });
            return data;
        }
    }
}
