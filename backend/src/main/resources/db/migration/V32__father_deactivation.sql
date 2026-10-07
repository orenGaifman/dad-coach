-- DC-B08: the admin's "deactivate" (playbook §43: deactivate first, then a typed permanent delete). Deactivating sets
-- the father PAUSED (no dashboard sign-in, every session revoked) and remembers the status he had, so reactivating
-- gives it back. Goes with the father (the purge deletes the father row).
CREATE TABLE father_deactivation (
    father_id       BIGINT      PRIMARY KEY REFERENCES father (id) ON DELETE CASCADE,
    previous_status VARCHAR(20) NOT NULL,
    deactivated_at  TIMESTAMPTZ NOT NULL,
    deactivated_by  UUID        REFERENCES staff_user (id) ON DELETE SET NULL
);
