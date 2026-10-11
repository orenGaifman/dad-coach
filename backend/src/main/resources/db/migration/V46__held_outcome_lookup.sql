-- V46 - D-042 (Unified Workflow Phase 6.1, platform spec PHASE6_SHARED_MESSAGING_SPEC "Spec v2"). Additive only.
--
-- The shared-number gateway now reports what became of a message it held (POST /api/integration/channel/held-outcome):
-- sent (with Meta's wamid), failed, unknown or expired. The report names the gateway's held id, so the two rows that
-- keep it ("held:<n>" in gateway_hold_id, V45) are looked up by it. Partial indexes: only held messages carry one.
-- No column changes: scheduled_response_delivery.status gains the value UNKNOWN (VARCHAR(20), no CHECK) and
-- login_link.receipt_status the value UNKNOWN (VARCHAR(20), no CHECK).

CREATE INDEX IF NOT EXISTS idx_scheduled_response_delivery_hold ON scheduled_response_delivery (gateway_hold_id)
    WHERE gateway_hold_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_login_link_hold ON login_link (gateway_hold_id)
    WHERE gateway_hold_id IS NOT NULL;
