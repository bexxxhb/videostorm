package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.CataloguePromotion;
import de.videostorm.indexing.application.port.out.ShowStaging;
import de.videostorm.indexing.domain.ParsedRating;
import de.videostorm.indexing.domain.ParsedShow;
import de.videostorm.indexing.domain.StagedShow;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The staging swap against a real PostgreSQL: promoting shows replaces the live catalogue with the
 * staged rows in one step — ids verbatim, ratings and their foreign keys intact — deletes whatever
 * was live before, and leaves movies untouched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(statements = {
        "DELETE FROM show_rating_staging", "DELETE FROM show_staging",
        "DELETE FROM show_rating", "DELETE FROM show",
        "DELETE FROM movie_rating", "DELETE FROM movie"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ShowPromotionIT extends PostgresIntegrationTestBase {

    @Autowired
    private ShowStaging staging;

    @Autowired
    private CataloguePromotion promotion;

    @Autowired
    private JdbcTemplate jdbc;

    private static StagedShow sampleShow() {
        ParsedRating tvdb = new ParsedRating("tvdb", new BigDecimal("9.5"), new BigDecimal("10"), 4200, true);
        ParsedRating imdb = new ParsedRating("imdb", new BigDecimal("9.4"), new BigDecimal("10"), 250000, false);
        ParsedShow parsed = new ParsedShow("Breaking Bad", "Breaking Bad", "2008-01-20",
                List.of(tvdb, imdb), List.of("Crime", "Drama"), "A plot.", "Ended",
                "tt0903747", "81189", "1396");
        return StagedShow.from(parsed, "Breaking Bad (2008)", "/media/shows/Breaking Bad (2008)", "<tvshow>raw</tvshow>");
    }

    @Test
    void promotesStagedShowsIntoLiveWithVerbatimIdsAndFields() {
        long stagedId = staging.stage(sampleShow());

        promotion.promote(SourceType.SHOWS);

        Map<String, Object> live = jdbc.queryForMap("SELECT * FROM show WHERE id = ?", stagedId);
        assertThat(live.get("title")).isEqualTo("Breaking Bad");
        assertThat(live.get("year")).isEqualTo(2008);
        assertThat(live.get("status")).isEqualTo("ENDED");
        assertThat(live.get("slug")).isEqualTo("breaking-bad-2008");
        assertThat(live.get("source_path")).isEqualTo("/media/shows/Breaking Bad (2008)");
        assertThat((BigDecimal) live.get("rating_value")).isEqualByComparingTo("9.5");
    }

    @Test
    void copiesEveryRatingAndTheForeignKeysSurviveTheCopy() {
        long stagedId = staging.stage(sampleShow());

        promotion.promote(SourceType.SHOWS);

        // Ratings are reachable only if their show_id still points at the copied show row.
        List<Map<String, Object>> ratings = jdbc.queryForList("""
                SELECT r.source FROM show_rating r JOIN show s ON s.id = r.show_id
                WHERE s.id = ? ORDER BY r.id
                """, stagedId);
        assertThat(ratings).extracting(r -> r.get("source")).containsExactly("tvdb", "imdb");
    }

    @Test
    void deletesThePreviousLiveCatalogueBeforeCopying() {
        Long oldId = jdbc.queryForObject("""
                INSERT INTO show (title, normalized_title, slug) VALUES ('Old Show', 'old show', 'old-show-0')
                RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO show_rating (show_id, source) VALUES (?, 'imdb')", oldId);

        staging.stage(sampleShow());
        promotion.promote(SourceType.SHOWS);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM show WHERE id = ?", Long.class, oldId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM show", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM show", String.class)).isEqualTo("Breaking Bad");
    }

    @Test
    void leavesMoviesUntouchedWhenPromotingShows() {
        jdbc.update("INSERT INTO movie (title, normalized_title, slug) VALUES ('A Film', 'a film', 'a-film-0')");
        staging.stage(sampleShow());

        promotion.promote(SourceType.SHOWS);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie", Long.class)).isEqualTo(1);
    }

    @Test
    void promotingMoviesIsANoOpOnTheShowCatalogue() {
        jdbc.update("INSERT INTO show (title, normalized_title, slug) VALUES ('Live', 'live', 'live-0')");
        staging.stage(sampleShow());

        promotion.promote(SourceType.MOVIES);

        // The live show is untouched and show staging is not swapped in.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM show", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM show", String.class)).isEqualTo("Live");
    }
}
