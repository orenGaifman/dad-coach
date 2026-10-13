-- D-063 (architecture effort, Tair D-124's port): a WhatsApp message from its WHATSAPP_INBOUND claim until its turn ran.
-- Written in the claim's transaction, deleted when the turn settled; a row whose lease ran out (the instance died) is
-- replayed.
CREATE TABLE pending_inbound (
    message_id  text PRIMARY KEY,
    sender      text NOT NULL,
    message     text NOT NULL,
    received_at timestamptz NOT NULL,
    lease_until timestamptz NOT NULL,
    attempts    integer NOT NULL DEFAULT 0
);
CREATE INDEX pending_inbound_lease ON pending_inbound (lease_until);
