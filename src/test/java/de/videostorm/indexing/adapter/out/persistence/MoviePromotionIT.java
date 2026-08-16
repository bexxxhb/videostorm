package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.CataloguePromotion;
import de.videostorm.indexing.application.port.out.MovieStaging;
import de.videostorm.indexing.domain.ParsedActor;
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
        "DELETE FROM movie_rating_staging", "DELETE FROM movie_actor_staging", "DELETE FROM movie_staging",
        "DELETE FROM movie_rating", "DELETE FROM movie_actor", "DELETE FROM movie",
        "DELETE FROM show_rating", "DELETE FROM show_actor", "DELETE FROM episode", "DELETE FROM show"
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
        ParsedActor lead = new ParsedActor("Liam Neeson", "Bryan Mills", 0, "http://img/neeson.jpg", "3896");
        ParsedActor support = new ParsedActor("Famke Janssen", "Lenore", 1, null, null);
        ParsedMovie parsed = new ParsedMovie("96 Hours - Taken 3", "Taken 3", 2014,
                List.of(tmdb, imdb), List.of("Action", "Thriller"), List.of(lead, support), 109, "A plot.",
                "Taken Collection", "133352", "tt2446042", null, "260346");
        return StagedMovie.from(parsed, "Taken 3 (2014)", "/media/movies/Taken 3 (2014)", 1_500_000_000L,
                "<movie>raw</movie>", "1080p");
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
        assertThat(live.get("resolution")).isEqualTo("1080p");
        assertThat(live.get("size_bytes")).isEqualTo(1_500_000_000L);
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
    void copiesEveryActorAndTheForeignKeysSurviveTheCopy() {
        long stagedId = staging.stage(sampleMovie());

        promotion.promote(SourceType.MOVIES);

        // Actors are reachable only if their movie_id still points at the copied movie row; billing
        // order is preserved and the optional sub-fields carry across, null where the .nfo omitted them.
        List<Map<String, Object>> actors = jdbc.queryForList("""
                SELECT a.name, a.role, a.thumb, a.tmdb_id FROM movie_actor a JOIN movie m ON m.id = a.movie_id
                WHERE m.id = ? ORDER BY a.billing_order
                """, stagedId);
        assertThat(actors).extracting(a -> a.get("name")).containsExactly("Liam Neeson", "Famke Janssen");
        assertThat(actors.get(0).get("role")).isEqualTo("Bryan Mills");
        assertThat(actors.get(0).get("tmdb_id")).isEqualTo("3896");
        assertThat(actors.get(1).get("thumb")).isNull();
        assertThat(actors.get(1).get("tmdb_id")).isNull();
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
