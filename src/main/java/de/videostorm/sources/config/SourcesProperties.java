package de.videostorm.sources.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Raw source-path configuration, one comma-separated value per type: {@code
 * VIDEOSTORM_SOURCES_MOVIES} and {@code VIDEOSTORM_SOURCES_SHOWS}. Absent values bind to empty
 * lists, which a later stage turns into validated {@link de.videostorm.sources.domain.SourcePaths}.
 */
@ConfigurationProperties(prefix = "videostorm.sources")
public record SourcesProperties(List<String> movies, List<String> shows) {

    public SourcesProperties {
        movies = movies == null ? List.of() : movies;
        shows = shows == null ? List.of() : shows;
    }
}
