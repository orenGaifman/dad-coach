-- D-027 (owner, 2026-10-07): the dashboard link Dad Coach sends on WhatsApp is a button that always works.
-- A sign-in link is no longer spent on use: it stays valid for a year (LoginLinkService.LINK_TTL) and every use
-- re-checks that the person may still sign in and opens a normal dashboard session. It ends when it expires or is
-- revoked - "log out everywhere", deactivation and deletion revoke every link of that person, like every session.
-- Only the token's hash is stored, as before.
--
-- used_at keeps its meaning (the first use); last_used_at and use_count follow the reuse.

ALTER TABLE login_link ADD COLUMN last_used_at   TIMESTAMPTZ;
ALTER TABLE login_link ADD COLUMN use_count      INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE login_link ADD COLUMN revoked_at     TIMESTAMPTZ;
ALTER TABLE login_link ADD COLUMN revoked_reason VARCHAR(40);

UPDATE login_link SET last_used_at = used_at, use_count = 1 WHERE used_at IS NOT NULL;
