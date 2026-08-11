package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.application.port.out.MovieStaging;
import de.videostorm.indexing.domain.DerivedTitle;
import de.videostorm.indexing.domain.DuplicateGuard;
import de.videostorm.indexing.domain.FeatureSelection;
import de.videostorm.indexing.domain.ParsedMovie;
import de.videostorm.indexing.domain.RecognizedVideo;
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
 * The real movie scan: walks each configured movie source path exactly one level deep, treats every
 * immediate subdirectory as one movie, and writes what it can to staging — the only part of a run that
 * touches the disks. The live catalogue is never touched here; swapping staging into live is a
 * separate step ({@code CataloguePromotion}), run once the scan has finished successfully.
 *
 * <p>Nothing is rejected for being thin. A folder with no {@code .nfo}, or one whose {@code .nfo} is
 * too broken to read, is catalogued from a folder-derived title with a zero year. Where several videos
 * sit together the feature is chosen by {@link FeatureSelection} and the rest recorded as ignored. A
 * folder with a metadata file but no video produces no entry and is recorded as a problem. Every gap
 * and every dropped file becomes a {@link RunIssue} on the returned {@link ScanReport}, so the run can
 * report what was thin without anything disappearing silently.
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

    private final SourcePaths sourcePaths;
    private final MovieStaging staging;
    private final EmbyMovieNfoParser parser = new EmbyMovieNfoParser();

    FilesystemMovieScan(SourcePaths sourcePaths, MovieStaging staging) {
        this.sourcePaths = sourcePaths;
        this.staging = staging;
    }

    @Override
    public SourceType type() {
        return SourceType.MOVIES;
    }

    @Override
    public ScanReport scan() {
        staging.clear();

        int found = 0;
        int indexed = 0;
        List<RunIssue> issues = new ArrayList<>();
        DuplicateGuard duplicates = new DuplicateGuard();
        for (SourcePath sourcePath : sourcePaths.pathsFor(SourceType.MOVIES)) {
            Path root = Path.of(sourcePath.value());
            if (!Files.isDirectory(root)) {
                log.warn("Movie source path is not a reachable directory, skipping: {}", root);
                continue;
            }
            for (Path folder : sortedChildDirectories(root)) {
                found++;
                if (stageMovie(folder, duplicates, issues)) {
                    indexed++;
                }
            }
        }
        log.info("Movie scan found {} folders, staged {}, recorded {} issues", found, indexed, issues.size());
        return new ScanReport(new RunCounts(found, indexed), issues);
    }

    /** Stages the folder as a movie where it holds a video, appending any issues it turned up. */
    private boolean stageMovie(Path folder, DuplicateGuard duplicates, List<RunIssue> issues) {
        List<Path> files = sortedRegularFiles(folder);
        List<Path> videos = files.stream()
                .filter(file -> RecognizedVideo.isVideoFile(file.getFileName().toString()))
                .toList();
        Path nfo = files.stream()
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(NFO_EXTENSION))
                .findFirst()
                .orElse(null);

        String raw = nfo == null ? null : read(nfo);
        ParsedMovie parsed = parseOrAbsent(raw);
        String folderName = folder.getFileName().toString();

        if (videos.isEmpty()) {
            if (nfo != null) {
                // Metadata but nothing to catalogue: no entry, recorded so the folder is not lost.
                issues.add(RunIssue.noVideo(pathOf(folder), DerivedTitle.resolve(parsed.title(), folderName)));
            }
            return false;
        }

        String path = pathOf(folder);
        StagedMovie movie = StagedMovie.from(parsed, folderName, path, raw);
        Optional<String> alreadyCatalogued = duplicates.claim(movie, path);
        if (alreadyCatalogued.isPresent()) {
            // Same film as a folder already staged this run: skip it, keeping both locations.
            issues.add(RunIssue.duplicate(path, movie.title(), alreadyCatalogued.get()));
            return false;
        }

        recordIgnoredVideos(folder, folderName, parsed, videos, issues);

        staging.stage(movie);
        recordThinFields(folder, movie, issues);
        return true;
    }

    private void recordIgnoredVideos(Path folder, String folderName, ParsedMovie parsed,
                                     List<Path> videos, List<RunIssue> issues) {
        if (videos.size() == 1) {
            return;
        }
        List<FeatureSelection.Video> candidates = videos.stream()
                .map(video -> new FeatureSelection.Video(video.getFileName().toString(), sizeOf(video)))
                .toList();
        FeatureSelection selection = FeatureSelection.choose(folderName, candidates);
        String title = DerivedTitle.resolve(parsed.title(), folderName);
        for (FeatureSelection.Video ignored : selection.ignored()) {
            issues.add(RunIssue.ignoredVideo(pathOf(folder.resolve(ignored.filename())), title));
        }
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
