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
        jdbc.update("DELETE FROM show_staging");
        jdbc.update("DELETE FROM show_rating");
        jdbc.update("DELETE FROM show");
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
