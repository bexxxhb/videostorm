package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.application.port.out.ShowStaging;
import de.videostorm.indexing.domain.ParsedShow;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunIssue;
import de.videostorm.indexing.domain.ScanReport;
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
import java.util.stream.Stream;

/**
 * The real show scan: walks each configured show source path exactly one level deep and treats every
 * immediate subdirectory as one show — the only part of a run that touches the disks. The live
 * catalogue is never touched here; swapping staging into live is a separate step
 * ({@code CataloguePromotion}), run once the scan has finished successfully.
 *
 * <p>Unlike a movie, a show is not gated on a video file: episodes are not looked at in this scope, so
 * every subdirectory becomes a show regardless of what it contains. Nothing is rejected for being
 * thin. The first {@code .nfo} in the show root — by deterministic codepoint order, regardless of its
 * filename — is parsed; a folder with no {@code .nfo}, or one whose {@code .nfo} is too broken to read,
 * is catalogued from a folder-derived title with a zero year. Every derived title and unknown year
 * becomes a {@link RunIssue} on the returned {@link ScanReport}, so the run can report what was thin
 * without anything disappearing silently.
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

    /** Stages the folder as a show, appending any thin-field issues it turned up. Never rejected. */
    private void stageShow(Path folder, List<RunIssue> issues) {
        Path nfo = firstNfo(folder);
        String raw = nfo == null ? null : read(nfo);
        ParsedShow parsed = parseOrAbsent(raw);
        String folderName = folder.getFileName().toString();

        StagedShow show = StagedShow.from(parsed, folderName, pathOf(folder), raw);
        staging.stage(show);
        recordThinFields(folder, show, issues);
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
