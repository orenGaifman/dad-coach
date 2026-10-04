package com.dadcoach.integration.platform.lifecycle;

import java.util.UUID;

/**
 * The father's id as the AI Workflow Platform knows it - the product's "person ref" (multi-tenancy): sent with
 * every turn and named by the deletion request. The father's external UUID ({@code new UUID(0, id)}), as the
 * API uses everywhere; never his phone.
 */
public final class PersonRefs {

    private PersonRefs() {
    }

    public static String of(Long fatherId) {
        return new UUID(0L, fatherId).toString();
    }

    /** The platform's id for a WhatsApp number ({@code whatsapp:+972...}), as every turn sends it. */
    public static String whatsappId(String phone) {
        return "whatsapp:" + phone;
    }
}
