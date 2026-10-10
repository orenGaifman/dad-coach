-- V45 - Phase 2 delivery reliability (D-038; docs/architecture/PHASE2_DADCOACH_SPEC.md). Additive only.
--
-- DC-B1: the Meta message id (wamid) of every message Dad Coach records - a scheduled coach message and a dashboard
-- link - is kept, so Meta's status receipts can be matched to it. A message the shared-number gateway held for later
-- has no wamid yet: its gateway id ("held:<n>") is kept apart and never matched as a wamid.
-- DC-B2: receipts move a row forward only. scheduled_response_delivery.status now distinguishes ACCEPTED (Meta's API
-- took it) and HELD (the gateway keeps it) from SENT / DELIVERED / READ (Meta's receipts) and FAILED; the column is
-- VARCHAR(20) with no CHECK, so no constraint changes. Rows written before V45 keep DELIVERED (= accepted, no wamid).
-- login_link.delivery_status keeps SENT / FAILED / ISSUED (read by the "already on his screen" check); the receipt
-- is in receipt_status, and a "failed" receipt also turns delivery_status FAILED.
-- Not UNIQUE: a constraint would fail the transaction after the message already went out; lookups use the indexes.

ALTER TABLE scheduled_response_delivery ADD COLUMN provider_message_id VARCHAR(128);
ALTER TABLE scheduled_response_delivery ADD COLUMN gateway_hold_id     VARCHAR(64);
ALTER TABLE scheduled_response_delivery ADD COLUMN status_at           TIMESTAMPTZ;
CREATE INDEX idx_scheduled_response_delivery_wamid ON scheduled_response_delivery (provider_message_id)
    WHERE provider_message_id IS NOT NULL;

ALTER TABLE login_link ADD COLUMN provider_message_id VARCHAR(128);
ALTER TABLE login_link ADD COLUMN gateway_hold_id     VARCHAR(64);
ALTER TABLE login_link ADD COLUMN receipt_status      VARCHAR(20);
ALTER TABLE login_link ADD COLUMN receipt_at          TIMESTAMPTZ;
CREATE INDEX idx_login_link_wamid ON login_link (provider_message_id) WHERE provider_message_id IS NOT NULL;
