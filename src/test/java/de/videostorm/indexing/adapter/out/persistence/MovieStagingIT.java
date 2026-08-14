package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.MovieStaging;
import de.videostorm.indexing.domain.ParsedActor;
import de.videostorm.indexing.domain.ParsedMovie;
import de.videostorm.indexing.domain.ParsedRating;
import de.videostorm.indexing.domain.StagedMovie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The staging writer against a real PostgreSQL and the V6 migration: a movie and its ratings land in
 * the staging mirrors, the explicit foreign key holds, staged ids are drawn from the same sequence as
 * the live table so they can never collide, and the live catalogue is never touched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(statements = {
        "DELETE FROM movie_rating_staging", "DELETE FROM movie_actor_staging", "DELETE FROM movie_staging",
        "DELETE FROM movie_rating", "DELETE FROM movie_actor", "DELETE FROM movie"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MovieStagingIT extends PostgresIntegrationTestBase {

    @Autowired
    private MovieStaging staging;

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
        return StagedMovie.from(parsed, "Taken 3 (2014)", "/media/movies/Taken 3 (2014)", "<movie>raw</movie>", "1080p");
    }

    @Test
    void writesTheMovieAndEveryRatingIntoStaging() {
        long id = staging.stage(sampleMovie());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM movie_staging WHERE id = ?", id);
        assertThat(row.get("title")).isEqualTo("96 Hours - Taken 3");
        assertThat(row.get("original_title")).isEqualTo("Taken 3");
        assertThat(row.get("year")).isEqualTo(2014);
        assertThat(row.get("normalized_title")).isEqualTo("96 hours taken 3");
        assertThat(row.get("normalized_original_title")).isEqualTo("taken 3");
        assertThat(row.get("genres")).isEqualTo("|Action|Thriller|");
        assertThat(row.get("runtime_minutes")).isEqualTo(109);
        assertThat(row.get("resolution")).isEqualTo("1080p");
        assertThat(row.get("plot")).isEqualTo("A plot.");
        assertThat(row.get("set_name")).isEqualTo("Taken Collection");
        assertThat(row.get("collection_id")).isEqualTo("133352");
        assertThat(row.get("imdb_id")).isEqualTo("tt2446042");
        assertThat(row.get("tmdb_id")).isEqualTo("260346");
        assertThat(row.get("raw_nfo")).isEqualTo("<movie>raw</movie>");
        assertThat(row.get("slug")).isEqualTo("96-hours-taken-3-2014");
        assertThat(row.get("source_path")).isEqualTo("/media/movies/Taken 3 (2014)");
        assertThat(row.get("derived_title")).isEqualTo(false);
        // Inline columns carry the default provider's rating for the listing.
        assertThat(row.get("rating_source")).isEqualTo("themoviedb");
        assertThat((BigDecimal) row.get("rating_value")).isEqualByComparingTo("6.3");

        List<Map<String, Object>> ratings = jdbc.queryForList(
                "SELECT source, value FROM movie_rating_staging WHERE movie_id = ? ORDER BY id", id);
        assertThat(ratings).hasSize(2);
        assertThat(ratings.get(0).get("source")).isEqualTo("themoviedb");
        assertThat(ratings.get(1).get("source")).isEqualTo("imdb");
    }

    @Test
    void writesEveryActorIntoStagingInBillingOrderWithOptionalFieldsNullable() {
        long id = staging.stage(sampleMovie());

        List<Map<String, Object>> actors = jdbc.queryForList(
                "SELECT name, role, billing_order, thumb, tmdb_id FROM movie_actor_staging WHERE movie_id = ? ORDER BY billing_order",
                id);
        assertThat(actors).extracting(a -> a.get("name")).containsExactly("Liam Neeson", "Famke Janssen");
        assertThat(actors.get(0).get("role")).isEqualTo("Bryan Mills");
        assertThat(actors.get(0).get("billing_order")).isEqualTo(0);
        assertThat(actors.get(0).get("thumb")).isEqualTo("http://img/neeson.jpg");
        assertThat(actors.get(0).get("tmdb_id")).isEqualTo("3896");
        // The supporting actor's optional sub-fields were absent and are stored as null.
        assertThat(actors.get(1).get("role")).isEqualTo("Lenore");
        assertThat(actors.get(1).get("thumb")).isNull();
        assertThat(actors.get(1).get("tmdb_id")).isNull();
    }

    @Test
    void clearEmptiesEveryStagingTable() {
        staging.stage(sampleMovie());

        staging.clear();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_rating_staging", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_actor_staging", Long.class)).isZero();
    }

    @Test
    void drawsStagedIdsFromTheLiveSequenceSoTheyNeverCollide() {
        Long liveId = jdbc.queryForObject("""
                INSERT INTO movie (title, normalized_title, slug) VALUES ('Live', 'live', 'live-0')
                RETURNING id
                """, Long.class);

        long stagedId = staging.stage(sampleMovie());

        assertThat(stagedId).isGreaterThan(liveId);
    }

    @Test
    void theForeignKeyRejectsARatingWithNoStagedMovie() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO movie_rating_staging (movie_id, source) VALUES (?, ?)", 999999L, "imdb"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theIdentityIndexRejectsASecondFilmWithTheSameIdentityTitleAndYear() {
        jdbc.update("INSERT INTO movie_staging (title, normalized_title, year, slug) VALUES ('Heat', 'heat', 1995, 'heat-1995')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO movie_staging (title, normalized_title, year, slug) VALUES ('Heat', 'heat', 1995, 'heat-1995-2')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theIdentityIndexTakesTheOriginalTitleWherePresentAndLeavesADifferentYearAlone() {
        jdbc.update("""
                INSERT INTO movie_staging (title, normalized_title, normalized_original_title, year, slug)
                VALUES ('Display One', 'display one', 'shared', 1999, 'a-1999')
                """);

        // Same identity (the original title) and year: rejected even though the display titles differ.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO movie_staging (title, normalized_title, normalized_original_title, year, slug)
                VALUES ('Display Two', 'display two', 'shared', 1999, 'b-1999')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A different year is a different film and is admitted.
        assertThat(jdbc.update("""
                INSERT INTO movie_staging (title, normalized_title, normalized_original_title, year, slug)
                VALUES ('Display Three', 'display three', 'shared', 2000, 'c-2000')
                """)).isEqualTo(1);
    }

    @Test
    void theImdbIndexRejectsASecondFilmWithTheSameImdbIdButAllowsManyWithNone() {
        jdbc.update("INSERT INTO movie_staging (title, normalized_title, year, slug, imdb_id) VALUES ('Heat', 'heat', 1995, 'heat-1995', 'tt0113277')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO movie_staging (title, normalized_title, year, slug, imdb_id) VALUES ('Heat Redux', 'heat redux', 1996, 'heat-redux-1996', 'tt0113277')"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A null imdb id is not an identity, so two unidentified films sit side by side.
        jdbc.update("INSERT INTO movie_staging (title, normalized_title, year, slug) VALUES ('Alpha', 'alpha', 2000, 'alpha-2000')");
        assertThat(jdbc.update(
                "INSERT INTO movie_staging (title, normalized_title, year, slug) VALUES ('Beta', 'beta', 2001, 'beta-2001')"))
                .isEqualTo(1);
    }

    @Test
    void leavesTheLiveCatalogueUntouched() {
        jdbc.update("INSERT INTO movie (title, normalized_title, slug) VALUES ('Live', 'live', 'live-0')");

        staging.clear();
        staging.stage(sampleMovie());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_rating", Long.class)).isZero();
    }
}
