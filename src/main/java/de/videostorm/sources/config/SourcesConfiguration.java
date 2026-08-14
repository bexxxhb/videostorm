package de.videostorm.sources.config;

import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Turns the two comma-separated source-path variables — {@code VIDEOSTORM_SOURCES_MOVIES} and
 * {@code VIDEOSTORM_SOURCES_SHOWS} — into a validated {@link SourcePaths} bean at context startup.
 *
 * <p>Each variable is injected as a {@code List<String>} by splitting the raw value on commas
 * (SpEL {@code split}). An unset variable resolves to an empty string, which splits to a single
 * blank element, and an empty or whitespace-only entry between commas splits likewise; all such
 * blanks are dropped before validation. A misconfiguration — a non-absolute path, a duplicate, or
 * one path nested in another — throws here and so aborts startup, naming the offending value.
 */
@Configuration
public class SourcesConfiguration {

    @Bean
    public SourcePaths sourcePaths(
            @Value("#{'${videostorm.sources.movies:}'.split(',')}") List<String> movies,
            @Value("#{'${videostorm.sources.shows:}'.split(',')}") List<String> shows) {
        return SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, withoutBlanks(movies),
                SourceType.SHOWS, withoutBlanks(shows)));
    }

    private static List<String> withoutBlanks(List<String> raw) {
        return raw.stream()
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }
}
