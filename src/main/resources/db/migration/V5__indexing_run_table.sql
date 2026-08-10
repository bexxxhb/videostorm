-- History of indexing runs. Rows are written when a run starts and updated once as it settles;
-- a re-index never deletes history, so the record of past runs survives every rebuild.

CREATE TABLE indexing_run (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type             TEXT NOT NULL,
    status           TEXT NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ,
    entries_found    INTEGER NOT NULL DEFAULT 0,
    entries_indexed  INTEGER NOT NULL DEFAULT 0
);

-- Backs the recent-runs listing: newest first.
CREATE INDEX idx_indexing_run_recent ON indexing_run (started_at DESC);

-- At most one run may be active across both types. The application enforces this under a lock;
-- this partial unique index is the backstop that makes a second concurrent active row impossible.
CREATE UNIQUE INDEX idx_indexing_run_single_active ON indexing_run (status) WHERE status = 'RUNNING';
