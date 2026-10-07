-- DC-B01 (D-005): dashboard sign-in. Copied from Tair's login-link + server-session model.
--
-- staff_user: the internal Dad Coach team (admin area). Created only through the ops API
--   (POST /api/ops/bootstrap-admin) - there is no sign-up.
-- login_link: a one-time, short-lived link sent over WhatsApp (or issued by the ops API). Only the
--   SHA-256 of the token is stored; a database read never yields a usable link.
-- dashboard_session: one signed-in browser behind the HttpOnly DADCOACH_SESSION cookie. Only the
--   token's hash is stored; revocation (logout, logout everywhere, deactivation) is immediate.
--
-- A link and a session belong to a father, a staff user, or both (one person who is both a father
-- and on the team signs in once). Both go with the father (ON DELETE CASCADE), so the father purge
-- (FatherDataPurger: DELETE FROM father) removes them too.

CREATE TABLE staff_user (
    id           UUID         PRIMARY KEY,
    phone        VARCHAR(32)  NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE login_link (
    id              UUID        PRIMARY KEY,
    father_id       BIGINT      REFERENCES father (id) ON DELETE CASCADE,
    staff_user_id   UUID        REFERENCES staff_user (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    -- How it reached the person: SENT / FAILED (WhatsApp) or ISSUED (handed to an operator, never sent).
    delivery_status VARCHAR(20) NOT NULL,
    delivery_error  VARCHAR(200),
    CONSTRAINT login_link_owner CHECK (father_id IS NOT NULL OR staff_user_id IS NOT NULL)
);
CREATE INDEX idx_login_link_father ON login_link (father_id);
CREATE INDEX idx_login_link_staff ON login_link (staff_user_id);
CREATE INDEX idx_login_link_failed ON login_link (created_at) WHERE delivery_status = 'FAILED';

CREATE TABLE dashboard_session (
    id              UUID         PRIMARY KEY,
    father_id       BIGINT       REFERENCES father (id) ON DELETE CASCADE,
    staff_user_id   UUID         REFERENCES staff_user (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64)  NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    last_seen_at    TIMESTAMPTZ  NOT NULL,
    idle_expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoked_reason  VARCHAR(40),
    user_agent      VARCHAR(400),
    CONSTRAINT dashboard_session_owner CHECK (father_id IS NOT NULL OR staff_user_id IS NOT NULL)
);
CREATE INDEX idx_dashboard_session_father ON dashboard_session (father_id);
CREATE INDEX idx_dashboard_session_staff ON dashboard_session (staff_user_id);
