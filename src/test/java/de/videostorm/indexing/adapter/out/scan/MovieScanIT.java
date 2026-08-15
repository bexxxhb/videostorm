package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.LibraryScan;
import de.videostorm.indexing.domain.FeatureVideo;
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
import java.io.RandomAccessFile;
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
        jdbc.update("DELETE FROM movie_actor_staging");
        jdbc.update("DELETE FROM movie_staging");
        jdbc.update("DELETE FROM movie_rating");
        jdbc.update("DELETE FROM movie_actor");
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
    void stagesTheCastOfAMovieAndRecordsNoMissingCastIssue() {
        movieFolder("Taken 3 (2014)", """
                <movie>
                  <title>Taken 3</title>
                  <year>2014</year>
                  <actor><name>Liam Neeson</name><role>Bryan Mills</role><order>0</order><tmdbid>3896</tmdbid></actor>
                  <actor><name>Famke Janssen</name><role>Lenore</role><order>1</order></actor>
                </movie>
                """, "taken3.mkv");

        ScanReport report = scan.scan(SourceType.MOVIES);

        Long id = jdbc.queryForObject("SELECT id FROM movie_staging", Long.class);
        List<Map<String, Object>> actors = jdbc.queryForList(
                "SELECT name, tmdb_id FROM movie_actor_staging WHERE movie_id = ? ORDER BY billing_order", id);
        assertThat(actors).extracting(a -> a.get("name")).containsExactly("Liam Neeson", "Famke Janssen");
        assertThat(actors.get(0).get("tmdb_id")).isEqualTo("3896");
        assertThat(report.issues())
                .noneMatch(issue -> issue.type() == RunIssueType.MISSING_FIELD && "cast".equals(issue.field()));
    }

    @Test
    void countsAMovieWithNoCastAsMissingData() {
        Path folder = movieFolder("Heat (1995)", "<movie><title>Heat</title><year>1995</year></movie>", "heat.mkv");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_actor_staging", Long.class)).isZero();
        assertThat(report.issues())
                .filteredOn(issue -> issue.type() == RunIssueType.MISSING_FIELD && "cast".equals(issue.field()))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.path()).isEqualTo(folder.toString());
                    assertThat(issue.title()).isEqualTo("Heat");
                });
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
    void cataloguesOnlyFoldersWithAFeatureAndIgnoresAMetadataOnlyFolder() {
        movieFolder("Real Movie", "<movie><title>Real</title></movie>", "real.avi");
        Path metadataOnly = createFolder("Just Metadata");
        write(metadataOnly.resolve("movie.nfo"), "<movie><title>Stranded</title></movie>");
        write(metadataOnly.resolve("notes.txt"), "not a video");

        RunCounts counts = scan.scan(SourceType.MOVIES).counts();

        // Only the folder with a feature is a movie; the metadata-only folder is neither found nor skipped.
        assertThat(counts).isEqualTo(new RunCounts(1, 1, 0));
        assertThat(jdbc.queryForObject("SELECT title FROM movie_staging", String.class)).isEqualTo("Real");
    }

    @Test
    void scanningShowsDoesNotTouchMovieStaging() {
        movieFolder("A Movie", "<movie><title>A</title></movie>", "a.mkv");

        // No show source path is configured here, so the show scan finds nothing; either way it must
        // never write the movie staging tables — the two types are scoped apart.
        ScanReport report = scan.scan(SourceType.SHOWS);

        assertThat(report.counts()).isEqualTo(RunCounts.none());
        assertThat(report.issues()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isZero();
    }

    @Test
    void cataloguesAFolderWithNoMetadataFromADerivedTitleFlaggedAsSuch() {
        Path folder = createFolder("The Blob (1958)");
        largeVideo(folder.resolve("blob.mkv"));

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
        largeVideo(folder.resolve("film.mp4"));

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1));
        Map<String, Object> movie = jdbc.queryForMap("SELECT * FROM movie_staging");
        assertThat(movie.get("title")).isEqualTo("Half Written");
        assertThat(movie.get("derived_title")).isEqualTo(true);
    }

    @Test
    void derivesTheResolutionFromTheFeatureFilenameNormalisedToThePSuffixedForm() {
        movieFolder("Heat (1995)", "<movie><title>Heat</title><year>1995</year></movie>", "Heat.1080.BluRay.mkv");

        scan.scan(SourceType.MOVIES);

        // The filename carried a bare 1080; it is stored normalised to 1080p.
        assertThat(jdbc.queryForObject("SELECT resolution FROM movie_staging", String.class)).isEqualTo("1080p");
    }

    @Test
    void leavesResolutionNullWhenTheFeatureFilenameHasNoRecognisedToken() {
        movieFolder("Heat (1995)", "<movie><title>Heat</title><year>1995</year></movie>", "heat.mkv");

        scan.scan(SourceType.MOVIES);

        assertThat(jdbc.queryForObject("SELECT resolution FROM movie_staging", String.class)).isNull();
    }

    @Test
    void readsTheResolutionFromTheChosenFeatureNotATrailerAlongsideIt() {
        Path folder = movieFolder("Mad Max Fury Road", "<movie><title>Mad Max</title></movie>",
                "Mad Max Fury Road.2160p.mkv");
        write(folder.resolve("trailer.720p.mp4"), "small");

        scan.scan(SourceType.MOVIES);

        // The feature is the 2160p file; the 720p trailer must not supply the resolution.
        assertThat(jdbc.queryForObject("SELECT resolution FROM movie_staging", String.class)).isEqualTo("2160p");
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

        assertThat(report.counts()).isEqualTo(new RunCounts(0, 0, 0));
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
    void cataloguesEverySourceFolderOfTheSameFilmSoTheDuplicateScanCanRevealIt() {
        // De-duplication was removed: the same film in two folders is catalogued from both, on purpose,
        // so the duplicate-movie scan (issue #47) can later reveal the double for deliberate removal.
        movieFolder("Heat (1995)", "<movie><title>Heat</title><year>1995</year></movie>", "heat.mkv");
        movieFolder("Heat backup", "<movie><title>Heat</title><year>1995</year></movie>", "heat.mkv");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(2, 2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isEqualTo(2);
        assertThat(report.issues()).noneMatch(issue -> issue.type() == RunIssueType.DUPLICATE);
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
    void cataloguesFoldersDifferingOnlyInDiacriticsAndPunctuationSeparately() {
        // With de-duplication removed, even folders whose titles differ only in diacritics or
        // punctuation are each catalogued; nothing collapses them at index time any more.
        movieFolder("Amelie", "<movie><title>Amélie</title><year>2001</year></movie>", "a.mkv");
        movieFolder("Amelie 2", "<movie><title>amelie!</title><year>2001</year></movie>", "a.mkv");

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(2, 2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isEqualTo(2);
        assertThat(report.issues()).noneMatch(issue -> issue.type() == RunIssueType.DUPLICATE);
    }

    @Test
    void discoversAMovieNestedSeveralDirectoriesBelowTheRoot() {
        // Movies/Action/Taken 3 (2014)/ — the movie folder sits three levels below the source root.
        Path nested = createFolder("Movies/Action/Taken 3 (2014)");
        write(nested.resolve("movie.nfo"), "<movie><title>Taken 3</title><year>2014</year></movie>");
        largeVideo(nested.resolve("taken3.mkv"));

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1, 0));
        assertThat(jdbc.queryForObject("SELECT title FROM movie_staging", String.class)).isEqualTo("Taken 3");
    }

    @Test
    void discoversAMovieAtTheFifthLevelButNotOneDeeper() {
        // The root's immediate child is level 1, so a/b/c/d/<movie> is level 5 and a/b/c/d/e/<movie> is level 6.
        Path atFifth = createFolder("a/b/c/d/Level Five (2001)");
        write(atFifth.resolve("movie.nfo"), "<movie><title>Level Five</title></movie>");
        largeVideo(atFifth.resolve("five.mkv"));
        Path atSixth = createFolder("a/b/c/d/e/Level Six (2002)");
        write(atSixth.resolve("movie.nfo"), "<movie><title>Level Six</title></movie>");
        largeVideo(atSixth.resolve("six.mkv"));

        ScanReport report = scan.scan(SourceType.MOVIES);

        // The level-5 movie is discovered; the level-6 one is beyond the depth cap and never scanned.
        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1, 0));
        assertThat(jdbc.queryForObject("SELECT title FROM movie_staging", String.class)).isEqualTo("Level Five");
    }

    @Test
    void skipsADirectoryWhoseOnlyVideosAreBelowTheFeatureSize() {
        Path trailerOnly = createFolder("Only A Trailer (2014)");
        write(trailerOnly.resolve("movie.nfo"), "<movie><title>Only A Trailer</title></movie>");
        write(trailerOnly.resolve("trailer.1080p.mp4"), "far below the feature size");

        ScanReport report = scan.scan(SourceType.MOVIES);

        // A recognised video is present, but nothing near feature size: not a movie, counted as skipped.
        assertThat(report.counts()).isEqualTo(new RunCounts(0, 0, 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isZero();
    }

    @Test
    void countsBothAMovieAndASkippedTrailerFolderInOneRun() {
        movieFolder("Heat (1995)", "<movie><title>Heat</title><year>1995</year></movie>", "heat.mkv");
        Path trailerOnly = createFolder("Coming Soon");
        write(trailerOnly.resolve("teaser.mp4"), "tiny");

        RunCounts counts = scan.scan(SourceType.MOVIES).counts();

        assertThat(counts).isEqualTo(new RunCounts(1, 1, 1));
    }

    @Test
    void doesNotDescendIntoAMovieFoldersOwnSubdirectories() {
        movieFolder("Mad Max (2015)", "<movie><title>Mad Max</title></movie>", "Mad Max.mkv");
        // A featurette that is itself feature-sized, in an extras subfolder, must not become its own movie.
        Path extras = createFolder("Mad Max (2015)/featurettes");
        largeVideo(extras.resolve("making-of.mkv"));

        ScanReport report = scan.scan(SourceType.MOVIES);

        assertThat(report.counts()).isEqualTo(new RunCounts(1, 1, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_staging", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM movie_staging", String.class)).isEqualTo("Mad Max");
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
        largeVideo(folder.resolve(videoFile));
        return folder;
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a recognised video that reaches the feature-size threshold, so its folder is catalogued
     * as a movie. Written as a sparse file — its reported size is {@link FeatureVideo#MIN_BYTES} but it
     * consumes no real disk — so the size rule can be exercised without moving half a gigabyte.
     */
    private static void largeVideo(Path file) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(FeatureVideo.MIN_BYTES);
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
