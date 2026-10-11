-- V48 - D-046 (Unified Workflow Phase 6.3-DC, step DC2; platform spec PHASE6_2_SEND_API_SPEC "Spec v2"). Additive only.
--
-- A scheduled coach message (the platform's scheduled-response callback: session timers, daily check, weekly plan) may be
-- sent by the platform's Send API instead of Dad Coach calling Meta itself (switch MESSAGING_MODE_SCHEDULED=PLATFORM;
-- default DIRECT = no column is ever written). The same platform columns as login_link (V47), same meaning:
--   platform_message_id    the platform's message id (the idempotency key is the row's idempotency_key,
--                          'scheduled-response:' || trigger_id, so a report also matches before the id is known)
--   platform_request       the exact request bytes, committed BEFORE the first POST (spec v2.6): every re-POST and
--                          reconcile sends these bytes; cleared (NULL) once the outcome no longer needs them
--   platform_state         POSTING / AMBIGUOUS / OWNED / DIRECT / REFUSED / GAVE_UP (see V47)
--   platform_status        the platform's last status applied (QUEUED/HELD/ACCEPTED/DELIVERED/READ/FAILED/UNKNOWN)
--   platform_posts         POSTs made with these bytes
--   platform_first_post_at when the first POST was made
--   platform_next_at       when the reconcile job looks at the row again; NULL = nothing to do
-- status keeps its values (spec 7.4): the platform's QUEUED (and an ambiguous answer) leave it SENDING.

ALTER TABLE scheduled_response_delivery ADD COLUMN platform_message_id    VARCHAR(64);
ALTER TABLE scheduled_response_delivery ADD COLUMN platform_request       TEXT;
ALTER TABLE scheduled_response_delivery ADD COLUMN platform_state         VARCHAR(20);
ALTER TABLE scheduled_response_delivery ADD COLUMN platform_status        VARCHAR(20);
ALTER TABLE scheduled_response_delivery ADD COLUMN platform_posts         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scheduled_response_delivery ADD COLUMN platform_first_post_at TIMESTAMPTZ;
ALTER TABLE scheduled_response_delivery ADD COLUMN platform_next_at       TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_scheduled_response_delivery_platform_message ON scheduled_response_delivery (platform_message_id)
    WHERE platform_message_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_scheduled_response_delivery_platform_due ON scheduled_response_delivery (platform_next_at)
    WHERE platform_next_at IS NOT NULL;
