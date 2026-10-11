package com.dadcoach.integration.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * D-042 switch {@code GATEWAY_HELD_REPORTS} ({@code dad-coach.whatsapp.gateway-held-reports}, default off): the
 * platform gateway's held-message outcome reports are applied, and early receipts of every status are kept for an
 * unknown wamid. Off = the endpoint answers 404 and receipts are kept exactly as before (only "failed").
 */
@Component
public class GatewayHeldReports {

    private volatile boolean enabled;

    public GatewayHeldReports(@Value("${dad-coach.whatsapp.gateway-held-reports:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Tests flip the switch on the one cached context. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
