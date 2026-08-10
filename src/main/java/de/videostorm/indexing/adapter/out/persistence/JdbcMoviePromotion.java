package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.indexing.application.port.out.CataloguePromotion;
import de.videostorm.sources.domain.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes staged movies into the live catalogue in one transaction. Shows are not imported yet, so a
 * promotion for {@link SourceType#SHOWS} does nothing, mirroring the scan.
 *
 * <p>The swap uses {@code DELETE} rather than {@code TRUNCATE} so concurrent readers are never blocked
 * during it: under MVCC a listing loaded throughout the run keeps its snapshot of the previous rows
 * until this transaction commits, then sees the new rows whole. The staged rows are copied with
 * {@code OVERRIDING SYSTEM VALUE} so their ids — drawn from the live sequence at staging time — cross
 * over verbatim, which keeps the ratings' foreign keys pointing at the same movies. Ratings are the
 * child of the movie foreign key, so live rows are deleted child-first and inserted parent-first.
 */
@Repository
class JdbcMoviePromotion implements CataloguePromotion {

    private static final Logger log = LoggerFactory.getLogger(JdbcMoviePromotion.class);

    private final JdbcTemplate jdbc;

    JdbcMoviePromotion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void promote(SourceType type) {
        if (type != SourceType.MOVIES) {
            return;
        }
        jdbc.update("DELETE FROM movie_rating");
        jdbc.update("DELETE FROM movie");
        int movies = jdbc.update("INSERT INTO movie OVERRIDING SYSTEM VALUE SELECT * FROM movie_staging");
        jdbc.update("INSERT INTO movie_rating OVERRIDING SYSTEM VALUE SELECT * FROM movie_rating_staging");
        log.info("Promoted {} staged movies into the live catalogue", movies);
    }
}
