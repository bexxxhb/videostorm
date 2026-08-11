package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.ShowStaging;
import de.videostorm.indexing.domain.ParsedRating;
import de.videostorm.indexing.domain.ParsedShow;
import de.videostorm.indexing.domain.StagedShow;
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
 * The show staging writer against a real PostgreSQL and the V9 migration: a show and its ratings land
 * in the staging mirrors, the explicit foreign key holds, staged ids are drawn from the same sequence
 * as the live table so they can never collide, and the live catalogue is never touched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(statements = {
        "DELETE FROM show_rating_staging", "DELETE FROM show_staging",
        "DELETE FROM show_rating", "DELETE FROM show"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ShowStagingIT extends PostgresIntegrationTestBase {

    @Autowired
    private ShowStaging staging;

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
    void writesTheShowAndEveryRatingIntoStaging() {
        long id = staging.stage(sampleShow());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM show_staging WHERE id = ?", id);
        assertThat(row.get("title")).isEqualTo("Breaking Bad");
        assertThat(row.get("original_title")).isEqualTo("Breaking Bad");
        assertThat(row.get("year")).isEqualTo(2008);
        assertThat(row.get("normalized_title")).isEqualTo("breaking bad");
        assertThat(row.get("normalized_original_title")).isEqualTo("breaking bad");
        assertThat(row.get("status")).isEqualTo("ENDED");
        assertThat(row.get("genres")).isEqualTo("|Crime|Drama|");
        assertThat(row.get("plot")).isEqualTo("A plot.");
        assertThat(row.get("imdb_id")).isEqualTo("tt0903747");
        assertThat(row.get("tvdb_id")).isEqualTo("81189");
        assertThat(row.get("tmdb_id")).isEqualTo("1396");
        assertThat(row.get("raw_nfo")).isEqualTo("<tvshow>raw</tvshow>");
        assertThat(row.get("slug")).isEqualTo("breaking-bad-2008");
        assertThat(row.get("source_path")).isEqualTo("/media/shows/Breaking Bad (2008)");
        assertThat(row.get("derived_title")).isEqualTo(false);
        // Inline columns carry the default provider's rating for the listing.
        assertThat(row.get("rating_source")).isEqualTo("tvdb");
        assertThat((BigDecimal) row.get("rating_value")).isEqualByComparingTo("9.5");

        List<Map<String, Object>> ratings = jdbc.queryForList(
                "SELECT source, value FROM show_rating_staging WHERE show_id = ? ORDER BY id", id);
        assertThat(ratings).hasSize(2);
        assertThat(ratings.get(0).get("source")).isEqualTo("tvdb");
        assertThat(ratings.get(1).get("source")).isEqualTo("imdb");
    }

    @Test
    void clearEmptiesBothStagingTables() {
        staging.stage(sampleShow());

        staging.clear();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM show_staging", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM show_rating_staging", Long.class)).isZero();
    }

    @Test
    void drawsStagedIdsFromTheLiveSequenceSoTheyNeverCollide() {
        Long liveId = jdbc.queryForObject("""
                INSERT INTO show (title, normalized_title, slug) VALUES ('Live', 'live', 'live-0')
                RETURNING id
                """, Long.class);

        long stagedId = staging.stage(sampleShow());

        assertThat(stagedId).isGreaterThan(liveId);
    }

    @Test
    void theForeignKeyRejectsARatingWithNoStagedShow() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO show_rating_staging (show_id, source) VALUES (?, ?)", 999999L, "imdb"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leavesTheLiveCatalogueUntouched() {
        jdbc.update("INSERT INTO show (title, normalized_title, slug) VALUES ('Live', 'live', 'live-0')");

        staging.clear();
        staging.stage(sampleShow());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM show", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM show_rating", Long.class)).isZero();
    }
}
