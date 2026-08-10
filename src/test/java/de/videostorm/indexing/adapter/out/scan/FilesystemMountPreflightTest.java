package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.sources.domain.SourcePath;
import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real reachability probe against a temporary filesystem: a path passes only when it exists,
 * is a directory, is readable and holds at least one entry, and every path that fails any of those
 * is reported so a partly-mounted set of drives names all its casualties, not just the first.
 */
class FilesystemMountPreflightTest {

    @Test
    void reportsNothingWhenEveryConfiguredPathIsAReadableNonEmptyDirectory(@TempDir Path tempDir)
            throws IOException {
        Path first = populatedDirectory(tempDir, "films");
        Path second = populatedDirectory(tempDir, "more-films");

        FilesystemMountPreflight preflight = preflightFor(first, second);

        assertThat(preflight.unreachable(SourceType.MOVIES)).isEmpty();
    }

    @Test
    void reportsAPathThatDoesNotExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("unmounted");

        FilesystemMountPreflight preflight = preflightFor(missing);

        assertThat(preflight.unreachable(SourceType.MOVIES))
                .containsExactly(SourcePath.of(missing.toString()));
    }

    @Test
    void reportsAPathThatIsAFileRatherThanADirectory(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("not-a-dir"));

        FilesystemMountPreflight preflight = preflightFor(file);

        assertThat(preflight.unreachable(SourceType.MOVIES))
                .containsExactly(SourcePath.of(file.toString()));
    }

    @Test
    void reportsADirectoryThatExistsButHoldsNoEntries(@TempDir Path tempDir) throws IOException {
        Path empty = Files.createDirectory(tempDir.resolve("empty"));

        FilesystemMountPreflight preflight = preflightFor(empty);

        assertThat(preflight.unreachable(SourceType.MOVIES))
                .containsExactly(SourcePath.of(empty.toString()));
    }

    @Test
    void reportsEveryFailingPathInConfigurationOrderAlongsideTheReachableOnes(@TempDir Path tempDir)
            throws IOException {
        Path good = populatedDirectory(tempDir, "films");
        Path missing = tempDir.resolve("unmounted");
        Path empty = Files.createDirectory(tempDir.resolve("empty"));

        FilesystemMountPreflight preflight = preflightFor(good, missing, empty);

        assertThat(preflight.unreachable(SourceType.MOVIES)).containsExactly(
                SourcePath.of(missing.toString()), SourcePath.of(empty.toString()));
    }

    @Test
    void onlyChecksThePathsOfTheRequestedType(@TempDir Path tempDir) throws IOException {
        Path movies = populatedDirectory(tempDir, "films");
        Path missingShow = tempDir.resolve("unmounted-shows");
        SourcePaths sourcePaths = SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of(movies.toString()),
                SourceType.SHOWS, List.of(missingShow.toString())));

        FilesystemMountPreflight preflight = new FilesystemMountPreflight(sourcePaths);

        assertThat(preflight.unreachable(SourceType.MOVIES)).isEmpty();
        assertThat(preflight.unreachable(SourceType.SHOWS))
                .containsExactly(SourcePath.of(missingShow.toString()));
    }

    private static Path populatedDirectory(Path parent, String name) throws IOException {
        Path dir = Files.createDirectory(parent.resolve(name));
        Files.createFile(dir.resolve("entry"));
        return dir;
    }

    private static FilesystemMountPreflight preflightFor(Path... moviePaths) {
        List<String> raw = java.util.Arrays.stream(moviePaths).map(Path::toString).toList();
        return new FilesystemMountPreflight(
                SourcePaths.fromRaw(Map.of(SourceType.MOVIES, raw)));
    }
}
