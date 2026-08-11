-- Episodes (issue #16). Show owns Episode: each row carries the season and episode number parsed from
-- an episode filename, stored against its show. There is no season table — seasonNumber lives on the
-- episode row, as the spec's aggregate design dictates. Episodes are stored but displayed nowhere in
-- this scope, so there is no read-side JPA mapping; the importer writes them, the swap copies them.

CREATE TABLE episode (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    show_id        BIGINT NOT NULL REFERENCES show (id),
    season_number  INTEGER NOT NULL,
    episode_number INTEGER NOT NULL,
    -- (show, season, episode) is unique. The run-time guard normally skips a duplicate before it
    -- reaches here; this constraint is the schema backstop, mirroring the movie identity indexes.
    CONSTRAINT uq_episode_identity UNIQUE (show_id, season_number, episode_number)
);

CREATE INDEX idx_episode_show ON episode (show_id);

-- Staging mirror of the episode table, the same shape the movie and show aggregates use. LIKE ...
-- INCLUDING ALL copies columns, defaults, checks and the unique constraint's index, but never foreign
-- keys, so the staging FK to show_staging is added explicitly. INCLUDING ALL also gives the staging
-- table its own identity sequence; we drop that and repoint the id default at the LIVE episode
-- sequence, so staged ids are drawn from the same sequence as live and can be copied verbatim by the
-- swap without ever colliding with a live id.
CREATE TABLE episode_staging (LIKE episode INCLUDING ALL);
ALTER TABLE episode_staging ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE episode_staging ALTER COLUMN id SET DEFAULT nextval(pg_get_serial_sequence('episode', 'id'));
ALTER TABLE episode_staging
    ADD CONSTRAINT fk_episode_staging_show
    FOREIGN KEY (show_id) REFERENCES show_staging (id);
