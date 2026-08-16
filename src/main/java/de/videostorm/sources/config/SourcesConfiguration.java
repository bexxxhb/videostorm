package de.videostorm.sources.config;

import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Turns the two comma-separated source-path variables — {@code VIDEOSTORM_SOURCES_MOVIES} and
 * {@code VIDEOSTORM_SOURCES_SHOWS} — into a validated {@link SourcePaths} bean at context startup.
 *
 * <p>Each variable is injected as its raw string (empty when unset) and split on commas in plain
 * Java, so a path may safely contain any character. Empty or whitespace-only entries — an unset
 * variable, or a stray comma — are dropped before validation. A misconfiguration — a non-absolute
 * path, a duplicate, or one path nested in another — throws here and so aborts startup, naming the
 * offending value.
 */
@Configuration
public class SourcesConfiguration {

    @Bean
    public SourcePaths sourcePaths(
            @Value("${videostorm.sources.movies:}") String movies,
            @Value("${videostorm.sources.shows:}") String shows) {
        return SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, split(movies),
                SourceType.SHOWS, split(shows)));
    }

    private static List<String> split(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }
}
