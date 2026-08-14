package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.application.port.out.ShowStaging;
import de.videostorm.indexing.domain.EpisodeDuplicateGuard;
import de.videostorm.indexing.domain.EpisodeNumberParser;
import de.videostorm.indexing.domain.ParsedEpisodeNumber;
import de.videostorm.indexing.domain.ParsedShow;
import de.videostorm.indexing.domain.RecognizedVideo;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunIssue;
import de.videostorm.indexing.domain.ScanReport;
import de.videostorm.indexing.domain.StagedEpisode;
import de.videostorm.indexing.domain.StagedShow;
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
 * The real show scan: walks each configured show source path exactly one level deep and treats every
 * immediate subdirectory as one show — the only part of a run that touches the disks. The live
 * catalogue is never touched here; swapping staging into live is a separate step
 * ({@code CataloguePromotion}), run once the scan has finished successfully.
 *
 * <p>Unlike a movie, a show is not gated on a video file: every subdirectory becomes a show regardless
 * of what it contains. Nothing is rejected for being thin. The first {@code .nfo} in the show root — by
 * deterministic codepoint order, regardless of its filename — is parsed; a folder with no {@code .nfo},
 * or one whose {@code .nfo} is too broken to read, is catalogued from a folder-derived title with a
 * zero year. Every derived title and unknown year becomes a {@link RunIssue} on the returned
 * {@link ScanReport}, so the run can report what was thin without anything disappearing silently.
 *
 * <p>Episode video files are collected recursively at any depth beneath the show folder — per-season
 * and per-episode subfolders both occur — and their season and episode numbers are read from the
 * filename by {@link EpisodeNumberParser}. A file whose number cannot be parsed is skipped and
 * recorded, never catalogued under a guess. Within one show, two files resolving to the same season
 * and episode are caught by an {@link EpisodeDuplicateGuard}: the first in codepoint order is kept and
 * the second is recorded with both paths. A show whose files all fail to parse is still catalogued,
 * with zero episodes — a mismatched naming convention shows up as an obvious anomaly rather than a
 * missing show.
 */
@Component
class FilesystemShowScan implements SourceScan {

    private static final Logger log = LoggerFactory.getLogger(FilesystemShowScan.class);
    private static final String NFO_EXTENSION = ".nfo";

    private final SourcePaths sourcePaths;
    private final ShowStaging staging;
    private final EmbyShowNfoParser parser = new EmbyShowNfoParser();

    FilesystemShowScan(SourcePaths sourcePaths, ShowStaging staging) {
        this.sourcePaths = sourcePaths;
        this.staging = staging;
    }

    @Override
    public SourceType type() {
        return SourceType.SHOWS;
    }

    @Override
    public ScanReport scan() {
        staging.clear();

        int found = 0;
        int indexed = 0;
        List<RunIssue> issues = new ArrayList<>();
        for (SourcePath sourcePath : sourcePaths.pathsFor(SourceType.SHOWS)) {
            Path root = Path.of(sourcePath.value());
            if (!Files.isDirectory(root)) {
                log.warn("Show source path is not a reachable directory, skipping: {}", root);
                continue;
            }
            for (Path folder : sortedChildDirectories(root)) {
                found++;
                stageShow(folder, issues);
                indexed++;
            }
        }
        log.info("Show scan found {} folders, staged {}, recorded {} issues", found, indexed, issues.size());
        return new ScanReport(new RunCounts(found, indexed), issues);
    }

    /** Stages the folder as a show with its episodes, appending any issues it turned up. Never rejected. */
    private void stageShow(Path folder, List<RunIssue> issues) {
        Path nfo = firstNfo(folder);
        String raw = nfo == null ? null : read(nfo);
        ParsedShow parsed = parseOrAbsent(raw);
        String folderName = folder.getFileName().toString();

        StagedShow show = StagedShow.from(parsed, folderName, pathOf(folder), raw);
        long showId = staging.stage(show);
        recordThinFields(folder, show, issues);
        stageEpisodes(folder, show, showId, issues);
    }

    /**
     * Reads season and episode numbers from every episode video file beneath the show, staging those
     * that parse and recording those that do not — a parse failure or a within-show duplicate is
     * skipped and reported, never catalogued under a guess. Files are visited in codepoint order so the
     * "first" kept on a duplicate is a stable choice.
     */
    private void stageEpisodes(Path folder, StagedShow show, long showId, List<RunIssue> issues) {
        EpisodeDuplicateGuard duplicates = new EpisodeDuplicateGuard();
        List<StagedEpisode> episodes = new ArrayList<>();
        for (Path file : sortedVideoFilesRecursive(folder)) {
            String path = pathOf(file);
            Optional<ParsedEpisodeNumber> parsed = EpisodeNumberParser.parse(file.getFileName().toString());
            if (parsed.isEmpty()) {
                issues.add(RunIssue.skippedEpisode(path, show.title()));
                continue;
            }
            Optional<String> alreadyClaimed = duplicates.claim(parsed.get(), path);
            if (alreadyClaimed.isPresent()) {
                issues.add(RunIssue.duplicate(path, show.title(), alreadyClaimed.get()));
                continue;
            }
            episodes.add(StagedEpisode.from(parsed.get()));
        }
        staging.stageEpisodes(showId, episodes);
    }

    private Path firstNfo(Path folder) {
        return sortedRegularFiles(folder).stream()
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(NFO_EXTENSION))
                .findFirst()
                .orElse(null);
    }

    private void recordThinFields(Path folder, StagedShow show, List<RunIssue> issues) {
        String path = pathOf(folder);
        if (show.derivedTitle()) {
            issues.add(RunIssue.missingField(path, show.title(), RunIssue.TITLE_FIELD));
        }
        if (show.year() == 0) {
            issues.add(RunIssue.missingField(path, show.title(), RunIssue.YEAR_FIELD));
        }
        if (show.actors().isEmpty()) {
            issues.add(RunIssue.missingField(path, show.title(), RunIssue.CAST_FIELD));
        }
    }

    private ParsedShow parseOrAbsent(String raw) {
        if (raw == null) {
            return ParsedShow.absent();
        }
        try {
            return parser.parse(raw);
        } catch (NfoParseException e) {
            // A truncated, non-XML or wrong-rooted file is treated exactly as an absent one.
            log.warn("Unreadable .nfo treated as absent: {}", e.getMessage());
            return ParsedShow.absent();
        }
    }

    private static String pathOf(Path path) {
        return path.toAbsolutePath().toString();
    }

    private static List<Path> sortedChildDirectories(Path root) {
        try (Stream<Path> entries = Files.list(root)) {
            return sortedByName(entries.filter(Files::isDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list show source path " + root, e);
        }
    }

    private static List<Path> sortedRegularFiles(Path folder) {
        try (Stream<Path> entries = Files.list(folder)) {
            return sortedByName(entries.filter(Files::isRegularFile));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list show folder " + folder, e);
        }
    }

    /** Every recognised video file at any depth beneath the show, sorted by full path in codepoint order. */
    private static List<Path> sortedVideoFilesRecursive(Path folder) {
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> videos = new ArrayList<>(walk
                    .filter(Files::isRegularFile)
                    .filter(file -> RecognizedVideo.isVideoFile(file.getFileName().toString()))
                    .toList());
            // Full path, so the codepoint order is stable across season and per-episode subfolders.
            videos.sort(Comparator.comparing(Path::toString));
            return videos;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk show folder " + folder, e);
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
