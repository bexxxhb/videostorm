package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.application.port.out.MovieStaging;
import de.videostorm.indexing.domain.DerivedTitle;
import de.videostorm.indexing.domain.DuplicateGuard;
import de.videostorm.indexing.domain.FeatureSelection;
import de.videostorm.indexing.domain.FeatureVideo;
import de.videostorm.indexing.domain.ParsedMovie;
import de.videostorm.indexing.domain.RecognizedVideo;
import de.videostorm.indexing.domain.Resolution;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunIssue;
import de.videostorm.indexing.domain.ScanReport;
import de.videostorm.indexing.domain.StagedMovie;
import de.videostorm.sources.domain.SourcePath;
import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The real movie scan: walks each configured movie source path recursively — up to {@value #MAX_DEPTH}
 * directory levels below the root — looking for movie folders, the only part of a run that touches the
 * disks. The live catalogue is never touched here; swapping staging into live is a separate step
 * ({@code CataloguePromotion}), run once the scan has finished successfully.
 *
 * <p>A directory is a movie only when it holds a recognised video that reaches the feature-size
 * threshold ({@link FeatureVideo}); the largest-prefix feature is chosen among those, so a trailer or
 * sample below the threshold can never be catalogued in the film's place. A movie folder is a leaf: the
 * walk does not descend into its own subdirectories, so its extras, featurettes and artwork never
 * become their own movies. A directory that holds recognised video(s) but none large enough is not a
 * movie — it is counted as {@code skipped} (surfaced in the run history) and the walk carries on past
 * it. A dead-end folder with a metadata file but no video produces no entry and is recorded as a
 * problem, exactly as before.
 *
 * <p>Nothing else is rejected for being thin. A movie folder with no {@code .nfo}, or one whose
 * {@code .nfo} is too broken to read, is catalogued from a folder-derived title with a zero year. Where
 * several feature-sized videos sit together the feature is chosen by {@link FeatureSelection} and the
 * rest — together with any smaller clips — recorded as ignored. Every gap and every dropped file
 * becomes a {@link RunIssue} on the returned {@link ScanReport}, so the run can report what was thin
 * without anything disappearing silently.
 *
 * <p>A {@link DuplicateGuard} spanning the whole run catches the same film appearing in two folders:
 * the first is staged, the second is skipped and recorded as a duplicate naming both locations. A
 * duplicate never aborts the run, and two films that merely share a title but differ in year are both
 * catalogued.
 *
 * <p>Folder and file order is the deterministic codepoint sort, so "the first {@code .nfo}" is a
 * stable choice regardless of the filesystem's own ordering. This adapter scans movies only; shows
 * have their own {@link FilesystemShowScan}, and {@link RoutingLibraryScan} routes a run to the right
 * one by type.
 */
@Component
class FilesystemMovieScan implements SourceScan {

    private static final Logger log = LoggerFactory.getLogger(FilesystemMovieScan.class);
    private static final String NFO_EXTENSION = ".nfo";

    /** How many directory levels below a source root the walk descends; the root's children are level 1. */
    private static final int MAX_DEPTH = 5;

    private final SourcePaths sourcePaths;
    private final MovieStaging staging;
    private final EmbyMovieNfoParser parser = new EmbyMovieNfoParser();

    FilesystemMovieScan(SourcePaths sourcePaths, MovieStaging staging) {
        this.sourcePaths = sourcePaths;
        this.staging = staging;
    }

    /** The running tally a single scan builds up as it walks; folded into {@link RunCounts} at the end. */
    private static final class Tally {
        private int found;
        private int indexed;
        private int skipped;
    }

    @Override
    public SourceType type() {
        return SourceType.MOVIES;
    }

    @Override
    public ScanReport scan() {
        staging.clear();

        Tally tally = new Tally();
        List<RunIssue> issues = new ArrayList<>();
        DuplicateGuard duplicates = new DuplicateGuard();
        for (SourcePath sourcePath : sourcePaths.pathsFor(SourceType.MOVIES)) {
            Path root = Path.of(sourcePath.value());
            if (!Files.isDirectory(root)) {
                log.warn("Movie source path is not a reachable directory, skipping: {}", root);
                continue;
            }
            for (Path child : sortedChildDirectories(root)) {
                scanDirectory(child, 1, duplicates, issues, tally);
            }
        }
        log.info("Movie scan found {} movies, staged {}, skipped {}, recorded {} issues",
                tally.found, tally.indexed, tally.skipped, issues.size());
        return new ScanReport(new RunCounts(tally.found, tally.indexed, tally.skipped), issues);
    }

    /**
     * Classifies one directory and, unless it is a movie, keeps walking into its children up to the
     * depth cap. A directory with a feature-sized video is a movie and a leaf; one with only smaller
     * videos is skipped; one with none is a container the walk descends through.
     */
    private void scanDirectory(Path dir, int level, DuplicateGuard duplicates,
                               List<RunIssue> issues, Tally tally) {
        List<Path> files = sortedRegularFiles(dir);
        List<FeatureSelection.Video> videos = files.stream()
                .filter(file -> RecognizedVideo.isVideoFile(file.getFileName().toString()))
                .map(file -> new FeatureSelection.Video(file.getFileName().toString(), sizeOf(file)))
                .toList();
        List<FeatureSelection.Video> features = videos.stream()
                .filter(video -> FeatureVideo.isFeature(video.filename(), video.sizeBytes()))
                .toList();

        if (!features.isEmpty()) {
            tally.found++;
            if (stageMovie(dir, files, videos, features, duplicates, issues)) {
                tally.indexed++;
            }
            return;
        }

        if (!videos.isEmpty()) {
            // Recognised video(s) but none reaching the feature threshold: a trailer/sample folder, not
            // a movie. Counted so the run history shows how many candidates were passed over on size.
            tally.skipped++;
        }

        List<Path> childDirectories = sortedChildDirectories(dir);
        if (videos.isEmpty()) {
            recordMetadataWithoutVideo(dir, files, childDirectories, issues);
        }
        if (level < MAX_DEPTH) {
            for (Path child : childDirectories) {
                scanDirectory(child, level + 1, duplicates, issues, tally);
            }
        }
    }

    /** Stages the movie folder, appending any issues it turned up; false when a duplicate skips it. */
    private boolean stageMovie(Path folder, List<Path> files, List<FeatureSelection.Video> videos,
                               List<FeatureSelection.Video> features, DuplicateGuard duplicates,
                               List<RunIssue> issues) {
        Path nfo = firstNfo(files);
        String raw = nfo == null ? null : read(nfo);
        ParsedMovie parsed = parseOrAbsent(raw);
        String folderName = folder.getFileName().toString();

        String featureFilename = chooseFeatureFilename(folderName, features);
        String path = pathOf(folder);
        String resolution = Resolution.fromFilename(featureFilename).map(Resolution::display).orElse(null);
        StagedMovie movie = StagedMovie.from(parsed, folderName, path, raw, resolution);

        Optional<String> alreadyCatalogued = duplicates.claim(movie, path);
        if (alreadyCatalogued.isPresent()) {
            // Same film as a folder already staged this run: skip it, keeping both locations.
            issues.add(RunIssue.duplicate(path, movie.title(), alreadyCatalogued.get()));
            return false;
        }

        recordIgnoredVideos(folder, folderName, parsed, videos, featureFilename, issues);
        staging.stage(movie);
        recordThinFields(folder, movie, issues);
        return true;
    }

    /**
     * The filename of the feature: the single feature-sized video where there is one, otherwise the one
     * {@link FeatureSelection} picks from among the feature-sized videos — so a trailer or sample below
     * the threshold is never even a candidate for the feature.
     */
    private static String chooseFeatureFilename(String folderName, List<FeatureSelection.Video> features) {
        if (features.size() == 1) {
            return features.get(0).filename();
        }
        return FeatureSelection.choose(folderName, features).feature().filename();
    }

    /** Records every video that is not the chosen feature — smaller clips included — as ignored. */
    private void recordIgnoredVideos(Path folder, String folderName, ParsedMovie parsed,
                                     List<FeatureSelection.Video> videos, String featureFilename,
                                     List<RunIssue> issues) {
        String title = DerivedTitle.resolve(parsed.title(), folderName);
        for (FeatureSelection.Video video : videos) {
            if (!video.filename().equals(featureFilename)) {
                issues.add(RunIssue.ignoredVideo(pathOf(folder.resolve(video.filename())), title));
            }
        }
    }

    /** A dead-end folder with a metadata file but no video: no entry, recorded so it is not lost. */
    private void recordMetadataWithoutVideo(Path dir, List<Path> files, List<Path> childDirectories,
                                            List<RunIssue> issues) {
        if (!childDirectories.isEmpty()) {
            // A container the walk still descends into, not a folder that was meant to be a movie.
            return;
        }
        Path nfo = firstNfo(files);
        if (nfo == null) {
            return;
        }
        ParsedMovie parsed = parseOrAbsent(read(nfo));
        issues.add(RunIssue.noVideo(pathOf(dir), DerivedTitle.resolve(parsed.title(), dir.getFileName().toString())));
    }

    private void recordThinFields(Path folder, StagedMovie movie, List<RunIssue> issues) {
        String path = pathOf(folder);
        if (movie.derivedTitle()) {
            issues.add(RunIssue.missingField(path, movie.title(), RunIssue.TITLE_FIELD));
        }
        if (movie.year() == 0) {
            issues.add(RunIssue.missingField(path, movie.title(), RunIssue.YEAR_FIELD));
        }
    }

    private ParsedMovie parseOrAbsent(String raw) {
        if (raw == null) {
            return ParsedMovie.absent();
        }
        try {
            return parser.parse(raw);
        } catch (NfoParseException e) {
            // A truncated, non-XML or wrong-rooted file is treated exactly as an absent one.
            log.warn("Unreadable .nfo treated as absent: {}", e.getMessage());
            return ParsedMovie.absent();
        }
    }

    private static Path firstNfo(List<Path> files) {
        return files.stream()
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(NFO_EXTENSION))
                .findFirst()
                .orElse(null);
    }

    private static String pathOf(Path path) {
        return path.toAbsolutePath().toString();
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not size video file " + file, e);
        }
    }

    private static List<Path> sortedChildDirectories(Path root) {
        try (Stream<Path> entries = Files.list(root)) {
            return sortedByName(entries.filter(Files::isDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list movie source path " + root, e);
        }
    }

    private static List<Path> sortedRegularFiles(Path folder) {
        try (Stream<Path> entries = Files.list(folder)) {
            return sortedByName(entries.filter(Files::isRegularFile));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list movie folder " + folder, e);
        }
    }

    private static List<Path> sortedByName(Stream<Path> paths) {
        List<Path> sorted = new ArrayList<>(paths.toList());
        // Codepoint order, so the "first" .nfo and folder traversal are stable across filesystems.
        sorted.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return sorted;
    }

    private static String read(Path nfo) {
        try {
            return Files.readString(nfo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read .nfo " + nfo, e);
        }
    }
}
