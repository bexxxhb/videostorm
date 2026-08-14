package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.sources.domain.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes staged shows into the live catalogue in one transaction, the show counterpart of
 * {@link JdbcMoviePromotion}. Same swap shape: {@code DELETE} the live rows (child-first, so ratings,
 * actors and episodes go before the shows they reference) so MVCC readers keep the previous catalogue
 * until commit, then copy the staged rows across with {@code OVERRIDING SYSTEM VALUE} so ids — and the
 * ratings', actors' and episodes' foreign keys — cross over verbatim.
 */
@Repository
class JdbcShowPromotion implements CataloguePromotor {

    private static final Logger log = LoggerFactory.getLogger(JdbcShowPromotion.class);

    private final JdbcTemplate jdbc;

    JdbcShowPromotion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SourceType type() {
        return SourceType.SHOWS;
    }

    @Override
    @Transactional
    public void promote() {
        jdbc.update("DELETE FROM episode");
        jdbc.update("DELETE FROM show_rating");
        jdbc.update("DELETE FROM show_actor");
        jdbc.update("DELETE FROM show");
        int shows = jdbc.update("INSERT INTO show OVERRIDING SYSTEM VALUE SELECT * FROM show_staging");
        jdbc.update("INSERT INTO show_rating OVERRIDING SYSTEM VALUE SELECT * FROM show_rating_staging");
        jdbc.update("INSERT INTO show_actor OVERRIDING SYSTEM VALUE SELECT * FROM show_actor_staging");
        int episodes = jdbc.update("INSERT INTO episode OVERRIDING SYSTEM VALUE SELECT * FROM episode_staging");
        log.info("Promoted {} staged shows and {} episodes into the live catalogue", shows, episodes);
    }
}
