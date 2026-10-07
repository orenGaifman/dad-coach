package com.dadcoach.whatsapp.buttons;

import com.dadcoach.channel.dto.OutboundMessageDto.ReplyButton;
import com.dadcoach.whatsapp.ButtonIds;
import java.util.Optional;
import java.util.UUID;

/**
 * A button about one quality-time session: {@code dc:<action>:<session id>} (the same shape as Big Boss's
 * {@code fu:<action>:<task id>}). The "dc:" prefix is what the shared number's gateway routes back to Dad Coach.
 * A tapped id is still untrusted input - every tap re-checks that the session is the tapping father's own.
 */
public record SessionButton(Action action, UUID sessionId) {

    public enum Action {
        /** On the follow-up after a session: it happened - completed by Dad Coach, no AI turn. */
        DONE("done", "היה מעולה"),
        /** On the follow-up: it did not happen - handed to the coach, which records it and offers another time. */
        MISSED("missed", "לא יצא"),
        /** On the one-hour reminder: ideas for that session's child, sent by Dad Coach. */
        IDEAS("ideas", "רוצה רעיונות");

        private final String code;
        private final String title;

        Action(String code, String title) {
            this.code = code;
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    public String id() {
        return ButtonIds.PREFIX + action.code + ":" + sessionId;
    }

    public ReplyButton toReplyButton() {
        return new ReplyButton(id(), action.title);
    }

    public static Optional<SessionButton> parse(String id) {
        if (id == null || !id.startsWith(ButtonIds.PREFIX)) {
            return Optional.empty();
        }
        String[] parts = id.substring(ButtonIds.PREFIX.length()).split(":", -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        for (Action action : Action.values()) {
            if (action.code.equals(parts[0])) {
                try {
                    return Optional.of(new SessionButton(action, UUID.fromString(parts[1])));
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
