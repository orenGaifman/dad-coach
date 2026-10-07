-- DC-B07: the training videos inside the dashboard (Big Boss D-171 pattern). Only what onboarding needs:
-- whether a father started or finished a video. The catalog itself is a file (training/catalog.json).
CREATE TABLE training_progress (
    father_id    BIGINT      NOT NULL REFERENCES father (id) ON DELETE CASCADE,
    slug         VARCHAR(60) NOT NULL,
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (father_id, slug)
);
