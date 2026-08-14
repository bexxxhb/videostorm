-- Movie runs now skip directories that hold recognised video(s) but none large enough to be a feature
-- (issue #31). The count is recorded alongside the existing found/indexed tallies so the run history
-- can show how many candidate directories were passed over on size. Existing rows default to 0.
ALTER TABLE indexing_run ADD COLUMN entries_skipped INTEGER NOT NULL DEFAULT 0;
