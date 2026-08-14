-- Movies and shows gain a cast child table and its staging mirror, following the ratings pattern from
-- V6/V9 (issue #44). Emby writes each performer as a top-level <actor> node; every named actor is
-- recorded here, one row per performer, and the staging swap into live copies them across verbatim.

-- One row per named actor. name is required (a nameless <actor> is never staged); role, thumb and
-- tmdb_id are optional. billing_order preserves the actor's <order> so the cast can be shown
-- top-billed first. tmdb_id is the actor's TMDB person id, kept as TEXT like the title external ids.
CREATE TABLE movie_actor (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    movie_id      BIGINT NOT NULL REFERENCES movie (id),
    name          TEXT NOT NULL,
    role          TEXT,
    billing_order INTEGER,
    thumb         TEXT,
    tmdb_id       TEXT
);

CREATE INDEX idx_movie_actor_movie ON movie_actor (movie_id);

CREATE TABLE show_actor (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    show_id       BIGINT NOT NULL REFERENCES show (id),
    name          TEXT NOT NULL,
    role          TEXT,
    billing_order INTEGER,
    thumb         TEXT,
    tmdb_id       TEXT
);

CREATE INDEX idx_show_actor_show ON show_actor (show_id);

-- Staging mirrors of the cast child tables. LIKE ... INCLUDING ALL copies columns, defaults, checks and
-- indexes but never foreign keys, so the staging FK is added explicitly. INCLUDING ALL also gives each
-- staging table its own fresh identity sequence; we drop that and repoint the id default at the LIVE
-- table's sequence, so staged ids are drawn from the same sequence as live and can be copied verbatim
-- into the live table by the swap without ever colliding with a live id.
CREATE TABLE movie_actor_staging (LIKE movie_actor INCLUDING ALL);
ALTER TABLE movie_actor_staging ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE movie_actor_staging ALTER COLUMN id SET DEFAULT nextval(pg_get_serial_sequence('movie_actor', 'id'));
ALTER TABLE movie_actor_staging
    ADD CONSTRAINT fk_movie_actor_staging_movie
    FOREIGN KEY (movie_id) REFERENCES movie_staging (id);

CREATE TABLE show_actor_staging (LIKE show_actor INCLUDING ALL);
ALTER TABLE show_actor_staging ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE show_actor_staging ALTER COLUMN id SET DEFAULT nextval(pg_get_serial_sequence('show_actor', 'id'));
ALTER TABLE show_actor_staging
    ADD CONSTRAINT fk_show_actor_staging_show
    FOREIGN KEY (show_id) REFERENCES show_staging (id);
