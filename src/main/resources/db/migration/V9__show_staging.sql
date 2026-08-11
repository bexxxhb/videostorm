-- Shows gain the same staging apparatus movies got in V6, so the importer (issue #15) can scan and
-- parse shows into staging without touching the live catalogue, and the swap into live (#11) can copy
-- them across verbatim. Episodes are not touched yet — a show is the immediate subdirectory of a
-- configured show source path, nothing below it.

-- Emby writes several <rating> providers per show. The show table keeps only the default provider's
-- rating inline for the listing; every rating is recorded here, one row per provider. Empty in the
-- live schema until an import populates it and the swap copies it across.
CREATE TABLE show_rating (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES show (id),
    source  TEXT,
    value   NUMERIC(4, 1),
    max     NUMERIC(4, 1),
    votes   INTEGER
);

CREATE INDEX idx_show_rating_show ON show_rating (show_id);

-- Staging mirrors of the show aggregate. LIKE ... INCLUDING ALL copies columns, defaults, checks and
-- indexes but never foreign keys, so the staging FK is added explicitly. INCLUDING ALL also gives each
-- staging table its own fresh identity sequence; we drop that and repoint the id default at the LIVE
-- table's sequence, so staged ids are drawn from the same sequence as live and can be copied verbatim
-- into the live table by the swap (#11) without ever colliding with a live id.
CREATE TABLE show_staging (LIKE show INCLUDING ALL);
ALTER TABLE show_staging ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE show_staging ALTER COLUMN id SET DEFAULT nextval(pg_get_serial_sequence('show', 'id'));

CREATE TABLE show_rating_staging (LIKE show_rating INCLUDING ALL);
ALTER TABLE show_rating_staging ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE show_rating_staging ALTER COLUMN id SET DEFAULT nextval(pg_get_serial_sequence('show_rating', 'id'));
ALTER TABLE show_rating_staging
    ADD CONSTRAINT fk_show_rating_staging_show
    FOREIGN KEY (show_id) REFERENCES show_staging (id);
