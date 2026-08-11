-- Identity constraints (issue #14). A film is identified by its normalized identity title together
-- with its year, and by its imdb id where one is present. The identity title is the original title
-- where present, else the display title (which itself already falls back to the folder-derived name),
-- so COALESCE(normalized_original_title, normalized_title) mirrors StagedMovie.normalizedIdentityTitle
-- exactly. Because the identity title normalises away punctuation, capitalisation and diacritics, two
-- folders that differ only in those read as the same film; two films sharing a title but differing in
-- year stay distinct.
--
-- The importer detects and skips duplicates during the run (DuplicateGuard), so in normal operation a
-- duplicate never reaches these indexes. They are the schema backstop: should the run-time guard ever
-- be bypassed and a duplicate reach the tables, the write is rejected rather than silently admitted, so
-- a duplicate can never be promoted into the live catalogue. A rejection surfaces as a failed run, not
-- a skipped folder — the guard, not the index, is what turns a duplicate into a recorded skip. The
-- indexes live on the staging mirror as well as the live table so a stray duplicate is caught where it
-- is written, not only at promotion.

CREATE UNIQUE INDEX uq_movie_identity
    ON movie (COALESCE(normalized_original_title, normalized_title), year);
CREATE UNIQUE INDEX uq_movie_imdb
    ON movie (imdb_id) WHERE imdb_id IS NOT NULL;

CREATE UNIQUE INDEX uq_movie_staging_identity
    ON movie_staging (COALESCE(normalized_original_title, normalized_title), year);
CREATE UNIQUE INDEX uq_movie_staging_imdb
    ON movie_staging (imdb_id) WHERE imdb_id IS NOT NULL;
