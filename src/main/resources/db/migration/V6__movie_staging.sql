-- The movie aggregate gains a ratings child table and its staging mirror, so the importer can scan
-- and parse into staging without touching the live catalogue (issue #10). The staging swap into live
-- arrives in a later ticket (#11).

-- Emby writes several <rating> providers per film. The movie table keeps only the default provider's
-- rating inline for the listing; every rating is recorded here, one row per provider. Empty in the
-- live schema until an import populates it and the swap copies it across.
CREATE TABLE movie_rating (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movie (id),
    source   TEXT,
    value    NUMERIC(4, 1),
    max      NUMERIC(4, 1),
    votes    INTEGER
);

CREATE INDEX idx_movie_rating_movie ON movie_rating (movie_id);

-- Staging mirrors of the movie aggregate. LIKE ... INCLUDING ALL copies columns, defaults, checks
-- and indexes but never foreign keys, so the staging FK is added explicitly. INCLUDING ALL also gives
-- each staging table its own fresh identity sequence; we drop that and repoint the id default at the
-- LIVE table's sequence, so staged ids are drawn from the same sequence as live and can be copied
-- verbatim into the live table by the swap (#11) without ever colliding with a live id.
CREATE TABLE movie_staging (LIKE movie INCLUDING ALL);
ALTER TABLE movie_staging ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE movie_staging ALTER COLUMN id SET DEFAULT nextval(pg_get_serial_sequence('movie', 'id'));

CREATE TABLE movie_rating_staging (LIKE movie_rating INCLUDING ALL);
ALTER TABLE movie_rating_staging ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE movie_rating_staging ALTER COLUMN id SET DEFAULT nextval(pg_get_serial_sequence('movie_rating', 'id'));
ALTER TABLE movie_rating_staging
    ADD CONSTRAINT fk_movie_rating_staging_movie
    FOREIGN KEY (movie_id) REFERENCES movie_staging (id);
