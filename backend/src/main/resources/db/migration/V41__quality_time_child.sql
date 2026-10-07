-- One quality-time session can be with several of the father's children ("שעה וחצי עם מטר ונעם").
-- quality_time.child_id stays the session's first child; every further child of the same session is a row here.
-- Deleting the session or the child (the father purge deletes both) removes the row with it.
CREATE TABLE IF NOT EXISTS quality_time_child (
    quality_time_id UUID NOT NULL REFERENCES quality_time(id) ON DELETE CASCADE,
    child_id BIGINT NOT NULL REFERENCES child(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (quality_time_id, child_id)
);

CREATE INDEX IF NOT EXISTS idx_quality_time_child_child_id ON quality_time_child(child_id);
