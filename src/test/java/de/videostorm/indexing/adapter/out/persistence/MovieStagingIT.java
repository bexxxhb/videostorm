package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.MovieStaging;
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
        "DELETE FROM movie_rating_staging", "DELETE FROM movie_staging",
        "DELETE FROM movie_rating", "DELETE FROM movie"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MovieStagingIT extends PostgresIntegrationTestBase {

    @Autowired
    private MovieStaging staging;

    @Autowired
    private JdbcTemplate jdbc;

    private static StagedMovie sampleMovie() {
        ParsedRating tmdb = new ParsedRating("themoviedb", new BigDecimal("6.3"), new BigDecimal("10"), 4200, true);
        ParsedRating imdb = new ParsedRating("imdb", new BigDecimal("6.0"), new BigDecimal("10"), 250000, false);
        ParsedMovie parsed = new ParsedMovie("96 Hours - Taken 3", "Taken 3", 2014,
                List.of(tmdb, imdb), List.of("Action", "Thriller"), 109, "A plot.",
                "Taken Collection", "133352", "tt2446042", null, "260346");
        return StagedMovie.from(parsed, "/media/movies/Taken 3 (2014)", "<movie>raw</movie>");
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
    void clearEmptiesBothStagingTables() {
        staging.stage(sampleMovie());

        staging.clear();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_rating_staging", Long.class)).isZero();
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
    void leavesTheLiveCatalogueUntouched() {
        jdbc.update("INSERT INTO movie (title, normalized_title, slug) VALUES ('Live', 'live', 'live-0')");

        staging.clear();
        staging.stage(sampleMovie());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_rating", Long.class)).isZero();
    }
}
