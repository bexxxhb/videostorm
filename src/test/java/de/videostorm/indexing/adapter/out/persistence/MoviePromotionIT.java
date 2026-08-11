package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.CataloguePromotion;
import de.videostorm.indexing.application.port.out.MovieStaging;
import de.videostorm.indexing.domain.ParsedMovie;
import de.videostorm.indexing.domain.ParsedRating;
import de.videostorm.indexing.domain.StagedMovie;
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
 * The staging swap against a real PostgreSQL: promoting movies replaces the live catalogue with the
 * staged rows in one step — ids verbatim, ratings and their foreign keys intact — deletes whatever
 * was live before, leaves shows untouched, and does nothing for a type with no importer yet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(statements = {
        "DELETE FROM movie_rating_staging", "DELETE FROM movie_staging",
        "DELETE FROM movie_rating", "DELETE FROM movie",
        "DELETE FROM show_rating", "DELETE FROM episode", "DELETE FROM show"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MoviePromotionIT extends PostgresIntegrationTestBase {

    @Autowired
    private MovieStaging staging;

    @Autowired
    private CataloguePromotion promotion;

    @Autowired
    private JdbcTemplate jdbc;

    private static StagedMovie sampleMovie() {
        ParsedRating tmdb = new ParsedRating("themoviedb", new BigDecimal("6.3"), new BigDecimal("10"), 4200, true);
        ParsedRating imdb = new ParsedRating("imdb", new BigDecimal("6.0"), new BigDecimal("10"), 250000, false);
        ParsedMovie parsed = new ParsedMovie("96 Hours - Taken 3", "Taken 3", 2014,
                List.of(tmdb, imdb), List.of("Action", "Thriller"), 109, "A plot.",
                "Taken Collection", "133352", "tt2446042", null, "260346");
        return StagedMovie.from(parsed, "Taken 3 (2014)", "/media/movies/Taken 3 (2014)", "<movie>raw</movie>");
    }

    @Test
    void promotesStagedMoviesIntoLiveWithVerbatimIdsAndFields() {
        long stagedId = staging.stage(sampleMovie());

        promotion.promote(SourceType.MOVIES);

        Map<String, Object> live = jdbc.queryForMap("SELECT * FROM movie WHERE id = ?", stagedId);
        assertThat(live.get("title")).isEqualTo("96 Hours - Taken 3");
        assertThat(live.get("year")).isEqualTo(2014);
        assertThat(live.get("normalized_original_title")).isEqualTo("taken 3");
        assertThat(live.get("slug")).isEqualTo("96-hours-taken-3-2014");
        assertThat(live.get("source_path")).isEqualTo("/media/movies/Taken 3 (2014)");
        assertThat((BigDecimal) live.get("rating_value")).isEqualByComparingTo("6.3");
    }

    @Test
    void copiesEveryRatingAndTheForeignKeysSurviveTheCopy() {
        long stagedId = staging.stage(sampleMovie());

        promotion.promote(SourceType.MOVIES);

        // Ratings are reachable only if their movie_id still points at the copied movie row.
        List<Map<String, Object>> ratings = jdbc.queryForList("""
                SELECT r.source FROM movie_rating r JOIN movie m ON m.id = r.movie_id
                WHERE m.id = ? ORDER BY r.id
                """, stagedId);
        assertThat(ratings).extracting(r -> r.get("source")).containsExactly("themoviedb", "imdb");
    }

    @Test
    void deletesThePreviousLiveCatalogueBeforeCopying() {
        Long oldId = jdbc.queryForObject("""
                INSERT INTO movie (title, normalized_title, slug) VALUES ('Old Film', 'old film', 'old-film-0')
                RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO movie_rating (movie_id, source) VALUES (?, 'imdb')", oldId);

        staging.stage(sampleMovie());
        promotion.promote(SourceType.MOVIES);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie WHERE id = ?", Long.class, oldId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM movie", String.class)).isEqualTo("96 Hours - Taken 3");
    }

    @Test
    void leavesShowsUntouchedWhenPromotingMovies() {
        jdbc.update("INSERT INTO show (title, normalized_title, slug) VALUES ('A Show', 'a show', 'a-show-0')");
        staging.stage(sampleMovie());

        promotion.promote(SourceType.MOVIES);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM show", Long.class)).isEqualTo(1);
    }

    @Test
    void promotingShowsIsANoOpOnTheMovieCatalogue() {
        jdbc.update("INSERT INTO movie (title, normalized_title, slug) VALUES ('Live', 'live', 'live-0')");
        staging.stage(sampleMovie());

        promotion.promote(SourceType.SHOWS);

        // The live movie is untouched and staging is not swapped in.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM movie", String.class)).isEqualTo("Live");
    }
}
