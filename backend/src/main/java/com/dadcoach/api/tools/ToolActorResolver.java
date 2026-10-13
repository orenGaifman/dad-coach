package com.dadcoach.api.tools;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.common.PhoneValidator;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * user_id → actor. Accepted: "+E164" (what the platform's Dad Coach tool client sends after stripping the channel)
 * or "{channel}:+E164" / "{channel}:E164 digits" (the platform's generic HTTP tool sends the identity as is, D-059).
 * Refused: anything else - in particular a bare numeric father id, which would let a caller name any father. A DELETED father is refused: his data is being deleted and no tool may touch it.
 */
@Component
public class ToolActorResolver {

    /** D-059: any "{channel}:" prefix, as the platform's own Dad Coach client accepted (and as family_context does). */
    private static final Pattern CHANNEL_QUALIFIED = Pattern.compile("^([A-Za-z][A-Za-z0-9_.-]*):(.+)$");

    private final FatherRepository fathers;

    public ToolActorResolver(FatherRepository fathers) {
        this.fathers = fathers;
    }

    public ToolActor resolve(String userId) {
        String raw = userId == null ? "" : userId.trim();
        Matcher channel = CHANNEL_QUALIFIED.matcher(raw);
        // a channel identity is a phone number, also without its plus; a bare number (a father id) never is
        String phone = channel.matches() ? PhoneValidator.normalizeToE164(channel.group(2).trim()) : raw;
        if (!phone.startsWith("+") || !PhoneValidator.isValidE164(phone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID",
                    "user_id must be the father's WhatsApp number (+E164 or whatsapp:+E164)");
        }
        Optional<Father> father = fathers.findByPhone(phone);
        if (father.filter(f -> f.getStatus() == FatherStatus.DELETED).isPresent()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FATHER_DELETED", "This father's data is being deleted");
        }
        return new ToolActor(phone, father);
    }
}
