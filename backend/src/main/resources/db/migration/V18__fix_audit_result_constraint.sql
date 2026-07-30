-- Add VALID to the chk_audit_result constraint
-- The validation endpoint logs VALID when an invitation token is successfully validated
ALTER TABLE invitation_audit_log DROP CONSTRAINT chk_audit_result;
ALTER TABLE invitation_audit_log ADD CONSTRAINT chk_audit_result
    CHECK (result IN ('SUCCESS', 'VALID', 'NOT_FOUND', 'EXPIRED', 'REVOKED', 'EXHAUSTED', 'RATE_LIMITED'));
