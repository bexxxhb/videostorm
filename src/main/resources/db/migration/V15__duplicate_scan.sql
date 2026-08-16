-- Outcome of each duplicate-movie scan (issue #47). A scan is a standalone maintenance action, not
-- part of re-indexing, and its history is retained indefinitely: nothing here is ever pruned, so the
-- record of every past scan survives every catalogue rebuild.

CREATE TABLE duplicate_scan_run (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    executed_at  TIMESTAMPTZ NOT NULL,
    duration_ms  BIGINT NOT NULL,
    group_count  INTEGER NOT NULL
);

-- Backs the run history table: newest first.
CREATE INDEX idx_duplicate_scan_run_recent ON duplicate_scan_run (executed_at DESC);

-- One detected group: every movie sharing one value under one criterion. Keyed by criterion and the
-- shared value (the IMDb id verbatim, or the lowercased-and-trimmed original title). A movie may back
-- rows in more than one group, so no uniqueness is imposed across groups.
CREATE TABLE duplicate_scan_group (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id       BIGINT NOT NULL REFERENCES duplicate_scan_run (id) ON DELETE CASCADE,
    criterion    TEXT NOT NULL,
    shared_value TEXT NOT NULL
);

CREATE INDEX idx_duplicate_scan_group_run ON duplicate_scan_group (run_id);

-- One movie inside a group, snapshotted at scan time so the run stays meaningful after the catalogue
-- changes. Only the three attributes the drill-down lists are kept; any may be absent.
CREATE TABLE duplicate_scan_member (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id       BIGINT NOT NULL REFERENCES duplicate_scan_group (id) ON DELETE CASCADE,
    imdb_id        TEXT,
    original_title TEXT,
    file_path      TEXT
);

CREATE INDEX idx_duplicate_scan_member_group ON duplicate_scan_member (group_id);
