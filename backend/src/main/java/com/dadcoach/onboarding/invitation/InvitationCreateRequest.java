package com.dadcoach.onboarding.invitation;

import java.util.Map;

/**
 * Request object for creating a new Invitation.
 *
 * @param type     the invitation type (SINGLE_USE or REUSABLE)
 * @param metadata optional metadata (referral_code, campaign_name, etc.)
 * @param maxUses  optional override for max_uses; if null, uses the type's default
 */
public record InvitationCreateRequest(
        InvitationType type,
        Map<String, Object> metadata,
        Integer maxUses
) {

    /**
     * Creates a request with the specified type, using type defaults for max_uses and no metadata.
     *
     * @param type the invitation type
     */
    public InvitationCreateRequest(InvitationType type) {
        this(type, null, null);
    }

    /**
     * Returns the effective max uses: the override value if provided, otherwise the type's default.
     *
     * @return the resolved max uses count
     */
    public int resolveMaxUses() {
        return maxUses != null ? maxUses : type.getDefaultMaxUses();
    }
}
