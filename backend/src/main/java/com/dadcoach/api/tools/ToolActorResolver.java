package com.dadcoach.api.tools;

import com.dadcoach.api.error.ApiException;
import com.dadcoach.common.PhoneValidator;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * user_id → actor. Accepted: "+E164" (what the platform's Dad Coach tool client sends after stripping the channel)
 * or "whatsapp:+E164". Refused: anything else - in particular a bare numeric father id, which would let a caller
 * name any father. A DELETED father is refused: his data is being deleted and no tool may touch it.
 */
@Component
public class ToolActorResolver {

    private static final Pattern WHATSAPP_PREFIX = Pattern.compile("^whatsapp:", Pattern.CASE_INSENSITIVE);

    private final FatherRepository fathers;

    public ToolActorResolver(FatherRepository fathers) {
        this.fathers = fathers;
    }

    public ToolActor resolve(String userId) {
        String phone = userId == null ? "" : WHATSAPP_PREFIX.matcher(userId.trim()).replaceFirst("").trim();
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
