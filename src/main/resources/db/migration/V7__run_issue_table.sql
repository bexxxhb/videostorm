-- Per-run issue detail: everything a scan found questionable, attached to the run that found it
-- (issue #13). Rows belong to the run history, never the catalogue, so a re-index reports afresh
-- without polluting the films it produced. A later ticket (#17) exports this as CSV and prunes the
-- detail beyond the last ten runs; the run summary in indexing_run is retained indefinitely.

CREATE TABLE indexing_run_issue (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id     BIGINT NOT NULL REFERENCES indexing_run (id),
    issue_type TEXT NOT NULL,
    path       TEXT NOT NULL,
    title      TEXT,
    field      TEXT
);

-- Backs both the per-run gap counts and the CSV export: every issue of one run, fetched together.
CREATE INDEX idx_run_issue_run ON indexing_run_issue (run_id);
