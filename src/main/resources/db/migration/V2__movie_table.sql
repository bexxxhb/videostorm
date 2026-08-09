-- The movie aggregate. The current read path (movie listing, paging and sort) only populates
-- and displays a subset of these columns; the rest (plot, set/collection, unique ids, raw nfo,
-- slug, source path, derived flags) exist now so the importer introduced by later tickets never
-- needs a schema change.

CREATE TABLE movie (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title            TEXT NOT NULL,
    original_title   TEXT,
    year             INTEGER NOT NULL DEFAULT 0,
    normalized_title TEXT NOT NULL,
    rating_source    TEXT,
    rating_value     NUMERIC(3, 1),
    rating_max       NUMERIC(3, 1),
    rating_votes     INTEGER,
    genres           TEXT,
    runtime_minutes  INTEGER,
    plot             TEXT,
    set_name         TEXT,
    collection_id    TEXT,
    imdb_id          TEXT,
    tvdb_id          TEXT,
    tmdb_id          TEXT,
    raw_nfo          TEXT,
    slug             TEXT NOT NULL,
    source_path      TEXT,
    derived_title    BOOLEAN NOT NULL DEFAULT FALSE,
    derived_year     BOOLEAN NOT NULL DEFAULT FALSE
);

-- Backs the fixed sort — normalized title ascending, with (year, id) as a deterministic
-- tiebreak — so paging can never duplicate or skip a row across a page boundary.
CREATE INDEX idx_movie_listing_sort ON movie (normalized_title, year, id);
