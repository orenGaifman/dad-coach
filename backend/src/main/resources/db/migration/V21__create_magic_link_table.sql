-- Magic link tokens for WhatsApp-to-Dashboard authentication
-- These short-lived tokens allow fathers to access their dashboard directly from WhatsApp

CREATE TABLE magic_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- The 32-character cryptographically secure token
    token VARCHAR(32) NOT NULL UNIQUE,
    
    -- The father this token authenticates (references father.id which is BIGINT)
    father_id BIGINT NOT NULL REFERENCES father(id) ON DELETE CASCADE,
    
    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    
    -- Redirect path after authentication (e.g., "/growth")
    redirect_path VARCHAR(255),
    
    -- Context for analytics (e.g., "quality_time_logged")
    context VARCHAR(50)
);

-- Index for token lookup (unique already creates an index, but being explicit)
CREATE INDEX idx_magic_link_token ON magic_link(token);

-- Index for finding active tokens by father
CREATE INDEX idx_magic_link_father_id ON magic_link(father_id);

-- Index for cleanup job (finding expired tokens)
CREATE INDEX idx_magic_link_expires_at ON magic_link(expires_at);

COMMENT ON TABLE magic_link IS 'Short-lived tokens for passwordless authentication from WhatsApp';
COMMENT ON COLUMN magic_link.token IS 'Cryptographically random 32-character token (~190 bits entropy)';
COMMENT ON COLUMN magic_link.consumed_at IS 'When the token was used; NULL means unused';
COMMENT ON COLUMN magic_link.context IS 'Why the link was generated, for analytics';
