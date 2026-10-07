-- Before V41 a session had exactly one child, so "Friday 09:00-10:30 with מטר and נעם" was booked as two sessions
-- with the same times - counted twice in the week's coverage and reminded twice. Merge such exact duplicates:
-- per father, SCHEDULED sessions with identical scheduled_start and scheduled_end are one session - the
-- earliest-created one keeps going and gets the other sessions' children; the others become CANCELLED (never
-- deleted). Idempotent: once merged a group has a single SCHEDULED row, so a re-run changes nothing.

WITH grouped AS (
    SELECT id, child_id,
           first_value(id) OVER (PARTITION BY father_id, scheduled_start, scheduled_end
                                 ORDER BY created_at, id) AS keeper_id
    FROM quality_time
    WHERE status = 'SCHEDULED'
),
moved AS (
    SELECT g.keeper_id, g.child_id FROM grouped g WHERE g.id <> g.keeper_id
    UNION
    SELECT g.keeper_id, qc.child_id FROM grouped g JOIN quality_time_child qc ON qc.quality_time_id = g.id
    WHERE g.id <> g.keeper_id
)
INSERT INTO quality_time_child (quality_time_id, child_id)
SELECT m.keeper_id, m.child_id
FROM moved m JOIN quality_time k ON k.id = m.keeper_id
WHERE m.child_id <> k.child_id
ON CONFLICT DO NOTHING;

WITH grouped AS (
    SELECT id,
           first_value(id) OVER (PARTITION BY father_id, scheduled_start, scheduled_end
                                 ORDER BY created_at, id) AS keeper_id
    FROM quality_time
    WHERE status = 'SCHEDULED'
)
UPDATE quality_time q
SET status = 'CANCELLED', updated_at = NOW()
FROM grouped g
WHERE q.id = g.id AND g.id <> g.keeper_id;
