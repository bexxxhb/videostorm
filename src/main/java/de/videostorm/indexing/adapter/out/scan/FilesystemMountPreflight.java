package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.application.port.out.MountPreflight;
import de.videostorm.sources.domain.SourcePath;
import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The real reachability probe: for each configured path of a type it asks the filesystem whether
 * the path is a readable directory holding at least one entry. An unmounted drive fails every one
 * of those, so the abort fires before the scan can mistake an empty tree for a wiped library.
 *
 * <p>A directory check ({@link Files#isDirectory}) already implies existence, so a missing path is
 * caught without a separate existence probe. An {@link IOException} while listing a directory is
 * treated as a failed check rather than propagated: the point of the probe is to decide reachable
 * or not, and an unlistable directory is not reachable.
 */
@Component
class FilesystemMountPreflight implements MountPreflight {

    private final SourcePaths sourcePaths;

    FilesystemMountPreflight(SourcePaths sourcePaths) {
        this.sourcePaths = sourcePaths;
    }

    @Override
    public List<SourcePath> unreachable(SourceType type) {
        List<SourcePath> failing = new ArrayList<>();
        for (SourcePath sourcePath : sourcePaths.pathsFor(type)) {
            if (!isReachable(Path.of(sourcePath.value()))) {
                failing.add(sourcePath);
            }
        }
        return List.copyOf(failing);
    }

    private static boolean isReachable(Path path) {
        return Files.isDirectory(path) && Files.isReadable(path) && hasAtLeastOneEntry(path);
    }

    private static boolean hasAtLeastOneEntry(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findFirst().isPresent();
        } catch (IOException e) {
            return false;
        }
    }
}
