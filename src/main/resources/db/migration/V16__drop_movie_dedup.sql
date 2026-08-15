-- Stop rejecting duplicate movies (issue #47 follow-up). The duplicate-movie scan exists to reveal
-- doubled entries that the source filesystems unavoidably contain, so those doubles must be allowed to
-- land in the catalogue rather than be turned away at write time. These identity/imdb unique indexes
-- were the schema backstop behind the indexer's in-run DuplicateGuard (also removed for movies); with
-- both gone, a second copy of a film is catalogued instead of skipped, and the scan surfaces it.
--
-- Movies only: the show tables keep their (show, season, episode) uniqueness, which has no
-- duplicate-scan counterpart to reveal or manage doubles.

DROP INDEX IF EXISTS uq_movie_identity;
DROP INDEX IF EXISTS uq_movie_imdb;
DROP INDEX IF EXISTS uq_movie_staging_identity;
DROP INDEX IF EXISTS uq_movie_staging_imdb;
