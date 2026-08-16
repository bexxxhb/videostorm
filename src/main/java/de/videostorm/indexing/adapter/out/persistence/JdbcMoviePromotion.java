package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.sources.domain.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes staged movies into the live catalogue in one transaction.
 *
 * <p>The swap uses {@code DELETE} rather than {@code TRUNCATE} so concurrent readers are never blocked
 * during it: under MVCC a listing loaded throughout the run keeps its snapshot of the previous rows
 * until this transaction commits, then sees the new rows whole. The staged rows are copied with
 * {@code OVERRIDING SYSTEM VALUE} so their ids — drawn from the live sequence at staging time — cross
 * over verbatim, which keeps the ratings' and actors' foreign keys pointing at the same movies. Ratings
 * and actors are children of the movie foreign key, so live rows are deleted child-first and inserted
 * parent-first.
 */
@Repository
class JdbcMoviePromotion implements CataloguePromotor {

    private static final Logger log = LoggerFactory.getLogger(JdbcMoviePromotion.class);

    private final JdbcTemplate jdbc;

    JdbcMoviePromotion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SourceType type() {
        return SourceType.MOVIES;
    }

    @Override
    @Transactional
    public void promote() {
        jdbc.update("DELETE FROM movie_rating");
        jdbc.update("DELETE FROM movie_actor");
        jdbc.update("DELETE FROM movie");
        int movies = jdbc.update("INSERT INTO movie OVERRIDING SYSTEM VALUE SELECT * FROM movie_staging");
        jdbc.update("INSERT INTO movie_rating OVERRIDING SYSTEM VALUE SELECT * FROM movie_rating_staging");
        jdbc.update("INSERT INTO movie_actor OVERRIDING SYSTEM VALUE SELECT * FROM movie_actor_staging");
        log.info("Promoted {} staged movies into the live catalogue", movies);
    }
}
