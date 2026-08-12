-- The movie gains a resolution column, derived by the importer from the feature video's filename
-- (the .nfo carries no resolution). It is added to the live table and its staging mirror alike, both
-- appended at the end so the promotion swap's positional `INSERT ... SELECT *` still lines the two up.
-- Existing rows stay NULL until the next re-index derives and populates the value.
ALTER TABLE movie ADD COLUMN resolution TEXT;
ALTER TABLE movie_staging ADD COLUMN resolution TEXT;
