-- V47 - D-045 (Unified Workflow Phase 6.3-DC, step DC1; platform spec PHASE6_2_SEND_API_SPEC "Spec v2"). Additive only.
--
-- A father's dashboard link may be sent by the platform's Send API (POST /api/v1/worker/messages/send) instead of
-- Dad Coach calling Meta itself (switch MESSAGING_MODE_LOGIN_LINK=PLATFORM; default DIRECT = no column is ever written).
--   platform_message_id    the platform's message id (its delivery reports name it; the idempotency key is
--                          'login-link:' || id, so a report also matches before the id is known)
--   platform_request       the exact request bytes, written and committed BEFORE the first POST (spec v2.6, M4): every
--                          re-POST and reconcile sends these bytes. They carry the sign-in link, so they are cleared
--                          (NULL) as soon as the outcome no longer needs them - the platform answered, Dad Coach sent it
--                          itself, or gave up. Only the token's hash is kept for good, as before.
--   platform_state         POSTING (bytes stored, first POST in flight) / AMBIGUOUS (an answer that may or may not have
--                          created the message: re-POST, then reconcile) / OWNED (the platform has it) / DIRECT (the
--                          platform provably created nothing; Dad Coach sent it itself) / REFUSED (400/422/409) /
--                          GAVE_UP (no answer for an hour)
--   platform_status        the platform's last status applied (QUEUED/HELD/ACCEPTED/DELIVERED/READ/FAILED/UNKNOWN)
--   platform_posts         POSTs made with these bytes (a 409 after the first one is adopted, spec v2.6)
--   platform_first_post_at when the first POST was made (reconcile by GET after MESSAGING_RECONCILE_AFTER, for 1 h)
--   platform_next_at       when the reconcile job looks at the row again; NULL = nothing to do
-- login_link.receipt_status gains the value QUEUED (VARCHAR(20), no CHECK): the platform has it and is still sending.

ALTER TABLE login_link ADD COLUMN platform_message_id    VARCHAR(64);
ALTER TABLE login_link ADD COLUMN platform_request       TEXT;
ALTER TABLE login_link ADD COLUMN platform_state         VARCHAR(20);
ALTER TABLE login_link ADD COLUMN platform_status        VARCHAR(20);
ALTER TABLE login_link ADD COLUMN platform_posts         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE login_link ADD COLUMN platform_first_post_at TIMESTAMPTZ;
ALTER TABLE login_link ADD COLUMN platform_next_at       TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_login_link_platform_message ON login_link (platform_message_id)
    WHERE platform_message_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_login_link_platform_due ON login_link (platform_next_at)
    WHERE platform_next_at IS NOT NULL;
