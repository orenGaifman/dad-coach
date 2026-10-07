-- The marketing site's signup form (site/src/signup-form.js): a father on a computer leaves his name and
-- mobile number instead of opening WhatsApp. One row per phone (E.164); a repeat submission updates the
-- name and the counters. Read by the admin ("site signups"); never routed to the AI, never messaged
-- automatically. Rows are deleted with the father's data-deletion request (by phone) like any personal data.
CREATE TABLE IF NOT EXISTS site_signup (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone              VARCHAR(20)  NOT NULL UNIQUE,
    name               VARCHAR(60)  NOT NULL,
    source             VARCHAR(20)  NOT NULL DEFAULT 'site',
    page               VARCHAR(200),
    submissions        INTEGER      NOT NULL DEFAULT 1,
    first_submitted_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_submitted_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_site_signup_last_submitted ON site_signup (last_submitted_at DESC);
