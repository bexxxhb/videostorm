package de.videostorm.sources.config;

import de.videostorm.sources.domain.SourcePath;
import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Logs each configured source path once at startup with whether it exists, is a directory and is
 * readable, so a bad mount can be diagnosed from the logs alone. This is deliberately the only
 * place a path value surfaces: paths are never rendered in the UI.
 */
@Component
public class SourcePathReachabilityLogger {

    private static final Logger log = LoggerFactory.getLogger(SourcePathReachabilityLogger.class);

    private final SourcePaths sourcePaths;

    public SourcePathReachabilityLogger(SourcePaths sourcePaths) {
        this.sourcePaths = sourcePaths;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logReachability() {
        for (SourceType type : SourceType.values()) {
            for (SourcePath sourcePath : sourcePaths.pathsFor(type)) {
                Path path = Path.of(sourcePath.value());
                log.info("Source path [{}] {}: exists={}, directory={}, readable={}",
                        type.label(), sourcePath.value(),
                        Files.exists(path), Files.isDirectory(path), Files.isReadable(path));
            }
        }
    }
}
