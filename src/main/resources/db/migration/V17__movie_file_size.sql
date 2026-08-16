-- The movie gains its feature video's size in bytes (issue #49), so the duplicate-scan drill-down can
-- show each entry's size next to its path. Added to the live table, its staging mirror and the
-- duplicate-scan member snapshot alike, all appended at the end so the promotion swap's positional
-- `INSERT ... SELECT *` still lines movie and movie_staging up. Existing rows stay NULL until the next
-- re-index derives and populates the value (same convention as V11's resolution column).
ALTER TABLE movie ADD COLUMN size_bytes BIGINT;
ALTER TABLE movie_staging ADD COLUMN size_bytes BIGINT;

ALTER TABLE duplicate_scan_member ADD COLUMN size_bytes BIGINT;
