-- D-027: settings the admin turns on and off from the admin screens, for the whole product (as Big Boss V27).
-- The first is whether the coach listens to WhatsApp voice notes. No row means the setting's default
-- (voice notes: on), so this table starts empty.
CREATE TABLE system_setting (
    key        VARCHAR(100) PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
