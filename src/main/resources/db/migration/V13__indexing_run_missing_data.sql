-- Runs now record how many catalogued entries had tracked data missing — a distinct-entry count of the
-- MISSING_FIELD issues (a derived title and/or an unknown year), so the run history can show it on every
-- row alongside Skipped (issue #34). Persisted like entries_skipped so it survives issue-detail pruning.
-- Existing rows default to 0.
ALTER TABLE indexing_run ADD COLUMN entries_missing_data INTEGER NOT NULL DEFAULT 0;
