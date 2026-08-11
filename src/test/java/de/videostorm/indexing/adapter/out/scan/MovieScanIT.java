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
 * The whole movie scan against a real filesystem and PostgreSQL: a temp library of movie folders is
 * walked one level deep, the first {@code .nfo} in each is parsed, and the results — with their
 * ratings — land in staging while the live catalogue is left exactly as it was.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MovieScanIT extends PostgresIntegrationTestBase {

    private static final Path MOVIES_DIR = createTempLibrary();

    @DynamicPropertySource
    static void movieSource(DynamicPropertyRegistry registry) {
        registry.add("videostorm.sources.movies", MOVIES_DIR::toString);
    }

    @Autowired
    private LibraryScan scan;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM movie_rating_staging");
        jdbc.update("DELETE FROM movie_staging");
        jdbc.update("DELETE FROM movie_rating");
        jdbc.update("DELETE FROM movie");
        emptyLibrary();
    }

    @Test
    void parsesEveryMovieFolderWithItsRatingsAndLeavesLiveUntouched() {
        jdbc.update("INSERT INTO movie (title, normalized_title, slug) VALUES ('Existing', 'existing', 'existing-0')");
        movieFolder("Taken 3 (2014)", """
                <movie>
                  <title>96 Hours - Taken 3</title>
                  <originaltitle>Taken 3</originaltitle>
                  <year>2014</year>
                  <ratings>
                    <rating name="themoviedb" max="10" default="true"><value>6.3</value><votes>4200</votes></rating>
                    <rating name="imdb" max="10"><value>6.0</value><votes>250000</votes></rating>
                  </ratings>
                  <genre>Action</genre>
                  <runtime>109</runtime>
                </movie>
                """, "taken3.mkv");
        movieFolder("The Thing (1982)", """
                <movie><title>The Thing</title><year>1982</year></movie>
                """, "thing.mp4");

        RunCounts counts = scan.scan(SourceType.MOVIES).counts();

        assertThat(counts).isEqualTo(new RunCounts(2, 2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isEqualTo(2);

        Map<String, Object> taken = jdbc.queryForMap(
                "SELECT * FROM movie_staging WHERE normalized_title = 'taken 3' OR normalized_original_title = 'taken 3'");
        assertThat(taken.get("title")).isEqualTo("96 Hours - Taken 3");
        assertThat(taken.get("year")).isEqualTo(2014);
        assertThat(taken.get("rating_source")).isEqualTo("themoviedb");
        assertThat(taken.get("source_path")).isEqualTo(MOVIES_DIR.resolve("Taken 3 (2014)").toString());
        Long takenId = ((Number) taken.get("id")).longValue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM movie_rating_staging WHERE movie_id = ?", Long.class, takenId))
                .isEqualTo(2);

        // Live catalogue untouched: still just the seeded row, no ratings.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_rating", Long.class)).isZero();
    }

    @Test
    void usesTheFirstNfoByCodepointOrderRegardlessOfName() {
        Path folder = movieFolder("Ambiguous", """
                <movie><title>From movie.nfo</title></movie>
                """, "feature.mkv");
        write(folder.resolve("alpha.nfo"), "<movie><title>From alpha.nfo</title></movie>");
        // The folder now holds alpha.nfo and movie.nfo; alpha sorts first by codepoint.

        scan.scan(SourceType.MOVIES);

        assertThat(jdbc.queryForObject("SELECT title FROM movie_staging", String.class))
                .isEqualTo("From alpha.nfo");
    }

    @Test
    void countsAFolderWithNoRecognisedVideoAsFoundButNotStaged() {
        movieFolder("Real Movie", "<movie><title>Real</title></movie>", "real.avi");
        movieFolder("Just Metadata", "<movie><title>Stranded</title></movie>", "notes.txt");

        RunCounts counts = scan.scan(SourceType.MOVIES).counts();

        assertThat(counts).isEqualTo(new RunCounts(2, 1));
        assertThat(jdbc.queryForObject("SELECT title FROM movie_staging", String.class)).isEqualTo("Real");
    }

    @Test
    void doesNotScanShowsYet() {
        movieFolder("A Movie", "<movie><title>A</title></movie>", "a.mkv");

        ScanReport report = scan.scan(SourceType.SHOWS);

        assertThat(report.counts()).isEqualTo(RunCounts.none());
        assertThat(report.issues()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isZero();
    }

    @Test
    void cataloguesAFolderWithNoMetadataFromADerivedTitleFlaggedAsSuch() {
        Path folder = createFolder("The Blob (1958)");
        write(folder.resolve("blob.mkv"), "video-bytes");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1));
        Map<String, Object> movie = jdbc.queryForMap("SELECT * FROM movie_staging");
        assertThat(movie.get("title")).isEqualTo("The Blob (1958)");
        assertThat(movie.get("derived_title")).isEqualTo(true);
        // The year is never read from the (1958) in the folder name.
        assertThat(movie.get("year")).isEqualTo(0);
        assertThat(report.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.type()).isEqualTo(RunIssueType.MISSING_FIELD);
                    assertThat(issue.field()).isEqualTo("title");
                    assertThat(issue.path()).isEqualTo(folder.toString());
                })
                .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("year"));
    }

    @Test
    void treatsAStructurallyBrokenNfoExactlyAsAnAbsentOne() {
        Path folder = createFolder("Half Written");
        write(folder.resolve("movie.nfo"), "<movie><title>Truncated");
        write(folder.resolve("film.mp4"), "video-bytes");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1));
        Map<String, Object> movie = jdbc.queryForMap("SELECT * FROM movie_staging");
        assertThat(movie.get("title")).isEqualTo("Half Written");
        assertThat(movie.get("derived_title")).isEqualTo(true);
    }

    @Test
    void choosesTheFeatureBySharedPrefixAndRecordsTheOthersAsIgnored() {
        Path folder = movieFolder("Mad Max Fury Road", "<movie><title>Mad Max</title></movie>",
                "Mad Max Fury Road.mkv");
        write(folder.resolve("trailer.mp4"), "small");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isEqualTo(1);
        assertThat(report.issues())
                .filteredOn(issue -> issue.type() == RunIssueType.IGNORED_VIDEO)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.path()).isEqualTo(folder.resolve("trailer.mp4").toString());
                    assertThat(issue.title()).isEqualTo("Mad Max");
                });
    }

    @Test
    void recordsAFolderWithMetadataButNoVideoAsAProblemWithoutCataloguingIt() {
        Path folder = createFolder("Just Metadata");
        write(folder.resolve("movie.nfo"), "<movie><title>Stranded</title></movie>");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isZero();
        assertThat(report.issues())
                .filteredOn(issue -> issue.type() == RunIssueType.NO_VIDEO)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.path()).isEqualTo(folder.toString());
                    assertThat(issue.title()).isEqualTo("Stranded");
                });
    }

    @Test
    void catchesASecondFolderOfTheSameFilmSkipsItAndRecordsBothPaths() {
        Path first = movieFolder("Heat (1995)", "<movie><title>Heat</title><year>1995</year></movie>", "heat.mkv");
        Path second = movieFolder("Heat backup", "<movie><title>Heat</title><year>1995</year></movie>", "heat.mkv");

        ScanReport report = scan.scan(SourceType.MOVIES);

        // Both folders were found; only the first was staged, and the run did not abort.
        assertThat(report.counts()).isEqualTo(new RunCounts(2, 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isEqualTo(1);
        assertThat(report.issues())
                .filteredOn(issue -> issue.type() == RunIssueType.DUPLICATE)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.path()).isEqualTo(second.toString());
                    assertThat(issue.title()).isEqualTo("Heat");
                    // The already-catalogued location is kept alongside the skipped one.
                    assertThat(issue.field()).isEqualTo(first.toString());
                });
    }

    @Test
    void cataloguesTwoFilmsThatShareATitleButDifferInYear() {
        movieFolder("The Thing (1982)", "<movie><title>The Thing</title><year>1982</year></movie>", "thing.mkv");
        movieFolder("The Thing (2011)", "<movie><title>The Thing</title><year>2011</year></movie>", "thing.mkv");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(2, 2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isEqualTo(2);
        assertThat(report.issues()).noneMatch(issue -> issue.type() == RunIssueType.DUPLICATE);
    }

    @Test
    void treatsFoldersDifferingOnlyInDiacriticsAndPunctuationAsTheSameFilm() {
        movieFolder("Amelie", "<movie><title>Amélie</title><year>2001</year></movie>", "a.mkv");
        movieFolder("Amelie 2", "<movie><title>amelie!</title><year>2001</year></movie>", "a.mkv");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(2, 1));
        assertThat(report.issues())
                .filteredOn(issue -> issue.type() == RunIssueType.DUPLICATE)
                .hasSize(1);
    }

    private Path createFolder(String name) {
        Path folder = MOVIES_DIR.resolve(name);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return folder;
    }

    private Path movieFolder(String name, String nfo, String videoFile) {
        Path folder = createFolder(name);
        write(folder.resolve("movie.nfo"), nfo);
        write(folder.resolve(videoFile), "video-bytes");
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
        try (Stream<Path> walk = Files.walk(MOVIES_DIR)) {
            List<Path> toDelete = walk.filter(path -> !path.equals(MOVIES_DIR))
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
            return Files.createTempDirectory("videostorm-movies");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
