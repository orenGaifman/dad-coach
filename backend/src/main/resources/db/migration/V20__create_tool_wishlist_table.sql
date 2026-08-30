-- V20: Create tool_wishlist table for AI-suggested tools tracking
-- This table stores tools that the AI wishes existed but don't yet.
-- Used for product feedback loop - see which capabilities users need most.

CREATE TABLE tool_wishlist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suggested_name VARCHAR(100) NOT NULL,
    user_need TEXT NOT NULL,
    suggested_capability TEXT NOT NULL,
    original_message TEXT,
    father_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    priority INTEGER,
    review_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    occurrence_count INTEGER NOT NULL DEFAULT 1,
    
    CONSTRAINT fk_tool_wishlist_father FOREIGN KEY (father_id) REFERENCES father(id) ON DELETE SET NULL
);

-- Indexes for common queries
CREATE INDEX idx_tool_wishlist_status ON tool_wishlist(status);
CREATE INDEX idx_tool_wishlist_suggested_name ON tool_wishlist(suggested_name);
CREATE INDEX idx_tool_wishlist_created_at ON tool_wishlist(created_at);

COMMENT ON TABLE tool_wishlist IS 'AI-suggested tools that do not exist yet - for product feedback loop';
COMMENT ON COLUMN tool_wishlist.suggested_name IS 'AI-suggested name for the tool (e.g., send_reminder)';
COMMENT ON COLUMN tool_wishlist.user_need IS 'What the user was trying to accomplish';
COMMENT ON COLUMN tool_wishlist.suggested_capability IS 'What capability the AI thinks this tool should have';
COMMENT ON COLUMN tool_wishlist.occurrence_count IS 'How many times this tool has been wished for';
