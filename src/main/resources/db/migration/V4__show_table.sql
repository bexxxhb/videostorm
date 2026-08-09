-- The show aggregate. Show owns Episode (not yet persisted as its own table in this scope).
-- The current read path (show listing, paging, sort and search) only populates and displays a
-- subset of these columns; the rest exist now so the importer introduced by later tickets never
-- needs a schema change.

CREATE TABLE show (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title                      TEXT NOT NULL,
    original_title             TEXT,
    year                       INTEGER NOT NULL DEFAULT 0,
    normalized_title           TEXT NOT NULL,
    normalized_original_title  TEXT,
    status                     TEXT NOT NULL DEFAULT 'UNKNOWN',
    rating_source              TEXT,
    rating_value               NUMERIC(3, 1),
    rating_max                 NUMERIC(3, 1),
    rating_votes               INTEGER,
    genres                     TEXT,
    plot                       TEXT,
    imdb_id                    TEXT,
    tvdb_id                    TEXT,
    tmdb_id                    TEXT,
    raw_nfo                    TEXT,
    slug                       TEXT NOT NULL,
    source_path                TEXT,
    derived_title              BOOLEAN NOT NULL DEFAULT FALSE,
    derived_year               BOOLEAN NOT NULL DEFAULT FALSE
);

-- Backs the fixed sort — normalized title ascending, with (year, id) as a deterministic
-- tiebreak — so paging can never duplicate or skip a row across a page boundary.
CREATE INDEX idx_show_listing_sort ON show (normalized_title, year, id);
