package de.videostorm.sources.config;

import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Turns the raw {@link SourcesProperties} into a validated {@link SourcePaths} bean at context
 * startup. A misconfiguration — a non-absolute path, a duplicate, or one path nested in another —
 * throws here and so aborts startup, naming the offending value.
 */
@Configuration
@EnableConfigurationProperties(SourcesProperties.class)
public class SourcesConfiguration {

    @Bean
    public SourcePaths sourcePaths(SourcesProperties properties) {
        return SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, properties.movies(),
                SourceType.SHOWS, properties.shows()));
    }
}
