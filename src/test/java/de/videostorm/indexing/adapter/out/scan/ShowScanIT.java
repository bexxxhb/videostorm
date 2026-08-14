package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.LibraryScan;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunIssueType;
import de.videostorm.indexing.domain.ScanReport;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole show scan against a real filesystem and PostgreSQL: a temp library of show folders is
 * walked one level deep, the first {@code .nfo} in each is parsed, and the results — with their
 * ratings — land in staging while the live catalogue is left exactly as it was. Unlike movies, a show
 * needs no video file: every immediate subdirectory is one show, since episodes are not looked at yet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ShowScanIT extends PostgresIntegrationTestBase {

    private static final Path SHOWS_DIR = createTempLibrary();

    @DynamicPropertySource
    static void showSource(DynamicPropertyRegistry registry) {
        registry.add("videostorm.sources.shows", SHOWS_DIR::toString);
    }

    @Autowired
    private LibraryScan scan;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM show_rating_staging");
        jdbc.update("DELETE FROM show_actor_staging");
        jdbc.update("DELETE FROM episode_staging");
        jdbc.update("DELETE FROM show_staging");
        jdbc.update("DELETE FROM show_rating");
        jdbc.update("DELETE FROM show_actor");
        jdbc.update("DELETE FROM episode");
        jdbc.update("DELETE FROM show");
        // The movie side is cleared too, symmetric to MovieScanIT: this class asserts a show scan
        // leaves the movie catalogue and its staging untouched, so both must start empty regardless of
        // what a movie IT staged before it.
        jdbc.update("DELETE FROM movie_rating_staging");
        jdbc.update("DELETE FROM movie_actor_staging");
        jdbc.update("DELETE FROM movie_staging");
        jdbc.update("DELETE FROM movie_rating");
        jdbc.update("DELETE FROM movie_actor");
        jdbc.update("DELETE FROM movie");
        emptyLibrary();
    }

    @Test
    void parsesEveryShowFolderWithItsRatingsAndLeavesLiveUntouched() {
        jdbc.update("INSERT INTO show (title, normalized_title, slug) VALUES ('Existing', 'existing', 'existing-0')");
        showFolder("Breaking Bad (2008)", """
                <tvshow>
                  <title>Breaking Bad</title>
                  <originaltitle>Breaking Bad</originaltitle>
                  <premiered>2008-01-20</premiered>
                  <status>Ended</status>
                  <ratings>
                    <rating name="tvdb" max="10" default="true"><value>9.5</value><votes>4200</votes></rating>
                    <rating name="imdb" max="10"><value>9.4</value><votes>250000</votes></rating>
                  </ratings>
                  <genre>Crime</genre>
                </tvshow>
                """);
        showFolder("The Office (2005)", """
                <tvshow><title>The Office</title><premiered>2005-03-24</premiered><status>Continuing</status></tvshow>
                """);

        RunCounts counts = scan.scan(SourceType.SHOWS).counts();

        assertThat(counts).isEqualTo(new RunCounts(2, 2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM show_staging", Long.class)).isEqualTo(2);

        Map<String, Object> breakingBad = jdbc.queryForMap(
                "SELECT * FROM show_staging WHERE normalized_title = 'breaking bad'");
        assertThat(breakingBad.get("title")).isEqualTo("Breaking Bad");
        assertThat(breakingBad.get("year")).isEqualTo(2008);
        assertThat(breakingBad.get("status")).isEqualTo("ENDED");
        assertThat(breakingBad.get("rating_source")).isEqualTo("tvdb");
        assertThat(breakingBad.get("source_path")).isEqualTo(SHOWS_DIR.resolve("Breaking Bad (2008)").toString());
        Long id = ((Number) breakingBad.get("id")).longValue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM show_rating_staging WHERE show_id = ?", Long.class, id))
                .isEqualTo(2);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM show_staging WHERE normalized_title = 'the office'", String.class))
                .isEqualTo("CONTINUING");

        // Live catalogue untouched: still just the seeded row, no ratings.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM show", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM show_rating", Long.class)).isZero();
    }

    @Test
    void stagesTheCastOfAShowAndRecordsNoMissingCastIssue() {
        showFolder("Breaking Bad (2008)", """
                <tvshow>
                  <title>Breaking Bad</title>
                  <premiered>2008-01-20</premiered>
                  <actor><name>Bryan Cranston</name><role>Walter White</role><order>0</order><tmdbid>17419</tmdbid></actor>
                  <actor><name>Aaron Paul</name><role>Jesse Pinkman</role><order>1</order></actor>
                </tvshow>
                """);

        ScanReport report = scan.scan(SourceType.SHOWS);

        Long id = jdbc.queryForObject("SELECT id FROM show_staging", Long.class);
        List<Map<String, Object>> actors = jdbc.queryForList(
                "SELECT name, tmdb_id FROM show_actor_staging WHERE show_id = ? ORDER BY billing_order", id);
        assertThat(actors).extracting(a -> a.get("name")).containsExactly("Bryan Cranston", "Aaron Paul");
        assertThat(actors.get(0).get("tmdb_id")).isEqualTo("17419");
        assertThat(report.issues())
                .noneMatch(issue -> issue.type() == RunIssueType.MISSING_FIELD && "cast".equals(issue.field()));
    }

    @Test
    void countsAShowWithNoCastAsMissingData() {
        showFolder("Castless (2020)", "<tvshow><title>Castless</title><premiered>2020-01-01</premiered></tvshow>");

        ScanReport report = scan.scan(SourceType.SHOWS);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM show_actor_staging", Long.class)).isZero();
        assertThat(report.issues())
                .filteredOn(issue -> issue.type() == RunIssueType.MISSING_FIELD && "cast".equals(issue.field()))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.path()).isEqualTo(SHOWS_DIR.resolve("Castless (2020)").toString());
                    assertThat(issue.title()).isEqualTo("Castless");
                });
    }

    @Test
    void treatsEveryImmediateSubdirectoryAsOneShowEvenWithNoVideoFile() {
        // A show folder holds seasons/episodes, not a feature video; it is catalogued regardless.
        Path folder = createFolder("Firefly (2002)");
        write(folder.resolve("tvshow.nfo"), """
                <tvshow><title>Firefly</title><premiered>2002-09-20</premiered><status>Ended</status></tvshow>
                """);
        createFolder("Firefly (2002)/Season 01");

        RunCounts counts = scan.scan(SourceType.SHOWS).counts();

        assertThat(counts).isEqualTo(new RunCounts(1, 1));
        assertThat(jdbc.queryForObject("SELECT title FROM show_staging", String.class)).isEqualTo("Firefly");
    }

    @Test
    void usesTheFirstNfoByCodepointOrderRegardlessOfName() {
        Path folder = createFolder("Ambiguous");
        write(folder.resolve("show.nfo"), "<tvshow><title>From show.nfo</title></tvshow>");
        write(folder.resolve("alpha.nfo"), "<tvshow><title>From alpha.nfo</title></tvshow>");
        // The folder holds alpha.nfo and show.nfo; alpha sorts first by codepoint.

        scan.scan(SourceType.SHOWS);

        assertThat(jdbc.queryForObject("SELECT title FROM show_staging", String.class))
                .isEqualTo("From alpha.nfo");
    }

    @Test
    void cataloguesAFolderWithNoMetadataFromADerivedTitleAndZeroYearFlaggingBoth() {
        createFolder("The Wire (2002)");

        ScanReport report = scan.scan(SourceType.SHOWS);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1));
        Map<String, Object> show = jdbc.queryForMap("SELECT * FROM show_staging");
        assertThat(show.get("title")).isEqualTo("The Wire (2002)");
        assertThat(show.get("derived_title")).isEqualTo(true);
        assertThat(show.get("status")).isEqualTo("UNKNOWN");
        // The year is never read from the (2002) in the folder name.
        assertThat(show.get("year")).isEqualTo(0);
        assertThat(report.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.type()).isEqualTo(RunIssueType.MISSING_FIELD);
                    assertThat(issue.field()).isEqualTo("title");
                    assertThat(issue.path()).isEqualTo(SHOWS_DIR.resolve("The Wire (2002)").toString());
                })
                .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("year"));
    }

    @Test
    void treatsAStructurallyBrokenNfoExactlyAsAnAbsentOne() {
        Path folder = createFolder("Half Written");
        write(folder.resolve("tvshow.nfo"), "<tvshow><title>Truncated");

        ScanReport report = scan.scan(SourceType.SHOWS);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1));
        Map<String, Object> show = jdbc.queryForMap("SELECT * FROM show_staging");
        assertThat(show.get("title")).isEqualTo("Half Written");
        assertThat(show.get("derived_title")).isEqualTo(true);
    }

    @Test
    void scanningShowsDoesNotTouchTheMovieCatalogue() {
        jdbc.update("INSERT INTO movie (title, normalized_title, slug) VALUES ('A Film', 'a film', 'a-film-0')");
        jdbc.update("INSERT INTO movie_staging (title, normalized_title, slug) VALUES ('Staged', 'staged', 'staged-0')");
        showFolder("A Show", "<tvshow><title>A Show</title></tvshow>");

        scan.scan(SourceType.SHOWS);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isEqualTo(1);
    }

    @Test
    void collectsEpisodesRecursivelyAtAnyDepthAndStoresSeasonAndEpisode() {
        Path show = showFolder("Firefly (2002)",
                "<tvshow><title>Firefly</title><premiered>2002-09-20</premiered></tvshow>");
        episodeFile(show, "Season 01/S01E01.mkv");
        episodeFile(show, "Season 01/S01E02.mkv");
        episodeFile(show, "Season 02/Episode 1/S02E01.mkv"); // a per-episode subfolder, one level deeper
        write(show.resolve("poster.jpg"), "not a video"); // artwork is ignored

        ScanReport report = scan.scan(SourceType.SHOWS);

        assertThat(episodesOf("firefly")).containsExactly("S1E1", "S1E2", "S2E1");
        assertThat(report.issues()).noneMatch(issue -> issue.type() == RunIssueType.SKIPPED_EPISODE);
    }

    @Test
    void extractsEverySupportedPatternThroughTheRealScan() {
        Path show = showFolder("Patterns", "<tvshow><title>Patterns</title></tvshow>");
        episodeFile(show, "S03E01.mkv");  // #1 -> S3E1
        episodeFile(show, "s03e05.mkv");  // #2 -> S3E5
        episodeFile(show, "Ep.02.mkv");   // #3 -> S1E2
        episodeFile(show, "103.mkv");     // #4 -> S1E3
        episodeFile(show, "6.06.mkv");    // #5 -> S6E6
        episodeFile(show, "s03.e11.mkv"); // #6 -> S3E11
        episodeFile(show, "s4e05.mkv");   // #7 -> S4E5
        episodeFile(show, "s5.e06.mkv");  // #8 -> S5E6

        scan.scan(SourceType.SHOWS);

        assertThat(episodesOf("patterns")).containsExactlyInAnyOrder(
                "S3E1", "S3E5", "S1E2", "S1E3", "S6E6", "S3E11", "S4E5", "S5E6");
    }

    @Test
    void skipsAndReportsAFileWhoseNumberCannotBeParsed() {
        Path show = showFolder("Mixed", "<tvshow><title>Mixed</title></tvshow>");
        episodeFile(show, "S01E01.mkv");
        episodeFile(show, "Behind the Scenes.mkv"); // no pattern -> skipped, not guessed

        ScanReport report = scan.scan(SourceType.SHOWS);

        assertThat(episodesOf("mixed")).containsExactly("S1E1");
        assertThat(report.issues()).anySatisfy(issue -> {
            assertThat(issue.type()).isEqualTo(RunIssueType.SKIPPED_EPISODE);
            assertThat(issue.path()).isEqualTo(show.resolve("Behind the Scenes.mkv").toString());
            assertThat(issue.title()).isEqualTo("Mixed");
        });
    }

    @Test
    void keepsTheFirstAndReportsTheSecondWhenTwoFilesResolveToTheSameEpisode() {
        Path show = showFolder("Ambiguous Show", "<tvshow><title>Ambiguous Show</title></tvshow>");
        // Both resolve to S01E01; "1.01.mkv" sorts before "S01E01.mkv" in codepoint order, so it is kept.
        episodeFile(show, "1.01.mkv");
        episodeFile(show, "S01E01.mkv");

        ScanReport report = scan.scan(SourceType.SHOWS);

        assertThat(episodesOf("ambiguous show")).containsExactly("S1E1");
        assertThat(report.issues()).anySatisfy(issue -> {
            assertThat(issue.type()).isEqualTo(RunIssueType.DUPLICATE);
            assertThat(issue.path()).isEqualTo(show.resolve("S01E01.mkv").toString());
            assertThat(issue.field()).isEqualTo(show.resolve("1.01.mkv").toString());
        });
    }

    @Test
    void cataloguesAShowWithZeroEpisodesWhenEveryFileFailsToParse() {
        Path show = showFolder("Odd Naming", "<tvshow><title>Odd Naming</title></tvshow>");
        episodeFile(show, "Pilot.mkv");
        episodeFile(show, "Finale.mkv");

        scan.scan(SourceType.SHOWS);

        // The show is still catalogued; it simply has no episodes.
        assertThat(jdbc.queryForObject(
                "SELECT title FROM show_staging WHERE normalized_title = 'odd naming'", String.class))
                .isEqualTo("Odd Naming");
        assertThat(episodesOf("odd naming")).isEmpty();
    }

    /** The episodes staged for a show as {@code S<season>E<episode>}, ordered by season then episode. */
    private List<String> episodesOf(String normalizedTitle) {
        return jdbc.query("""
                SELECT e.season_number, e.episode_number
                FROM episode_staging e JOIN show_staging s ON s.id = e.show_id
                WHERE s.normalized_title = ?
                ORDER BY e.season_number, e.episode_number
                """,
                (rs, row) -> "S" + rs.getInt("season_number") + "E" + rs.getInt("episode_number"),
                normalizedTitle);
    }

    private void episodeFile(Path showFolder, String relativePath) {
        Path file = showFolder.resolve(relativePath);
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        write(file, "");
    }

    private Path createFolder(String name) {
        Path folder = SHOWS_DIR.resolve(name);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return folder;
    }

    private Path showFolder(String name, String nfo) {
        Path folder = createFolder(name);
        write(folder.resolve("tvshow.nfo"), nfo);
        return folder;
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void emptyLibrary() {
        try (Stream<Path> walk = Files.walk(SHOWS_DIR)) {
            List<Path> toDelete = walk.filter(path -> !path.equals(SHOWS_DIR))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path path : toDelete) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createTempLibrary() {
        try {
            return Files.createTempDirectory("videostorm-shows");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
