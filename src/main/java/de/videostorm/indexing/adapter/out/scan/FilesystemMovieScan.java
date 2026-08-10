package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.application.port.out.LibraryScan;
import de.videostorm.indexing.application.port.out.MovieStaging;
import de.videostorm.indexing.domain.ParsedMovie;
import de.videostorm.indexing.domain.RecognizedVideo;
import de.videostorm.indexing.domain.RunCounts;
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
import java.util.stream.Stream;

/**
 * The real movie scan: walks each configured movie source path exactly one level deep, treats every
 * immediate subdirectory as one movie, parses the first {@code .nfo} it finds, and writes the result
 * to staging — the only part of a run that touches the disks. The live catalogue is never touched
 * here; the swap into live is a later ticket (#11).
 *
 * <p>Folder and file order is the deterministic codepoint sort, so "the first {@code .nfo}" is a
 * stable choice regardless of the filesystem's own ordering. Shows are not scanned yet: a run for
 * {@link SourceType#SHOWS} reports nothing found until a later ticket implements it.
 */
@Component
class FilesystemMovieScan implements LibraryScan {

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
    public RunCounts scan(SourceType type) {
        if (type != SourceType.MOVIES) {
            return RunCounts.none();
        }
        staging.clear();

        int found = 0;
        int indexed = 0;
        for (SourcePath sourcePath : sourcePaths.pathsFor(SourceType.MOVIES)) {
            Path root = Path.of(sourcePath.value());
            if (!Files.isDirectory(root)) {
                log.warn("Movie source path is not a reachable directory, skipping: {}", root);
                continue;
            }
            for (Path folder : sortedChildDirectories(root)) {
                found++;
                if (stageMovie(folder)) {
                    indexed++;
                }
            }
        }
        log.info("Movie scan found {} folders, parsed {} into staging", found, indexed);
        return new RunCounts(found, indexed);
    }

    private boolean stageMovie(Path folder) {
        List<Path> files = sortedRegularFiles(folder);
        boolean hasVideo = files.stream()
                .anyMatch(file -> RecognizedVideo.isVideoFile(file.getFileName().toString()));
        if (!hasVideo) {
            return false;
        }
        Path nfo = files.stream()
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(NFO_EXTENSION))
                .findFirst()
                .orElse(null);
        if (nfo == null) {
            return false;
        }
        String raw = read(nfo);
        ParsedMovie parsed = parser.parse(raw);
        staging.stage(StagedMovie.from(parsed, folder.toAbsolutePath().toString(), raw));
        return true;
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
