package com.dadcoach.integration.channel;

import com.dadcoach.common.PhoneValidator;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.father.FatherStatus;
import com.dadcoach.integration.platform.lifecycle.DeletedSenders;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shared WhatsApp gateway asks "is this phone yours?" before forwarding (playbook §33, claim-first, no default
 * route) - same contract as Tair and Big Boss: {@code POST /api/integration/channel/claim {"phone":"+E164"}} →
 * {@code {"claimed": true|false}}. Dad Coach claims a father who exists, is not DELETED and whose number is not
 * waiting for its platform deletion. Anything else - unknown, deleted, an invalid phone - is false, never a 4xx,
 * and the answer carries no data. Auth: the platform-callback key, always (its own security chain).
 */
@RestController
public class ChannelClaimController {

    private static final Logger log = LoggerFactory.getLogger(ChannelClaimController.class);

    public record ClaimRequest(String phone) {}

    private final FatherRepository fathers;
    private final DeletedSenders deletedSenders;

    public ChannelClaimController(FatherRepository fathers, DeletedSenders deletedSenders) {
        this.fathers = fathers;
        this.deletedSenders = deletedSenders;
    }

    @PostMapping("/api/integration/channel/claim")
    public Map<String, Boolean> claim(@RequestBody(required = false) ClaimRequest request) {
        String phone = normalize(request == null ? null : request.phone());
        boolean claimed = phone != null
                && fathers.findByPhone(phone).filter(f -> f.getStatus() != FatherStatus.DELETED).isPresent()
                && !deletedSenders.isDeleted(phone);
        log.atInfo().setMessage("channel.claim.result").addKeyValue("claimed", claimed).log();
        return Map.of("claimed", claimed);
    }

    /** E.164, with spaces, dashes, brackets and bidi marks removed and a missing "+" added; null when invalid. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\s\\-().\\u200e\\u200f\\u202a-\\u202e]", "");
        String e164 = PhoneValidator.normalizeToE164(cleaned);
        return PhoneValidator.isValidE164(e164) ? e164 : null;
    }
}
