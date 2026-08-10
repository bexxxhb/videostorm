package de.videostorm.sources.config;

import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reachability log is the operator's only window onto the configured paths, so it must state,
 * once per path, whether the path exists, is a directory and is readable.
 */
@ExtendWith(OutputCaptureExtension.class)
class SourcePathReachabilityLoggerTest {

    @Test
    void logsExistenceDirectorinessAndReadabilityOncePerConfiguredPath(
            CapturedOutput output, @org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path existingDir = Files.createDirectory(tempDir.resolve("movies"));
        Path missing = tempDir.resolve("shows");

        SourcePaths sourcePaths = SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of(existingDir.toString()),
                SourceType.SHOWS, List.of(missing.toString())));

        new SourcePathReachabilityLogger(sourcePaths).logReachability();

        assertThat(output).contains(
                "[movie] " + existingDir + ": exists=true, directory=true, readable=true");
        assertThat(output).contains(
                "[show] " + missing + ": exists=false, directory=false, readable=false");
    }
}
