package de.videostorm.sources.config;

import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the wiring from raw comma-separated configuration to a validated {@link SourcePaths}
 * bean: separate values per type bind and split, an empty type does not block startup, and an
 * overlapping or duplicated configuration aborts startup while naming the offending pair.
 */
class SourcesConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(SourcesConfiguration.class);

    @Test
    void readsSeparateCommaSeparatedValuesPerType() {
        contextRunner
                .withPropertyValues(
                        "videostorm.sources.movies=/media/movies,/mnt/films",
                        "videostorm.sources.shows=/media/shows")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SourcePaths paths = context.getBean(SourcePaths.class);
                    assertThat(paths.pathsFor(SourceType.MOVIES)).hasSize(2);
                    assertThat(paths.pathsFor(SourceType.SHOWS)).hasSize(1);
                });
    }

    @Test
    void startsWithNoSourcePathsConfiguredAtAll() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SourcePaths paths = context.getBean(SourcePaths.class);
            assertThat(paths.hasPathsFor(SourceType.MOVIES)).isFalse();
            assertThat(paths.hasPathsFor(SourceType.SHOWS)).isFalse();
        });
    }

    @Test
    void failsAtStartupNamingAnOverlappingPair() {
        contextRunner
                .withPropertyValues("videostorm.sources.movies=/media,/media/movies")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("/media")
                            .hasMessageContaining("/media/movies");
                });
    }

    @Test
    void failsAtStartupWhenAPathIsNotAbsolute() {
        contextRunner
                .withPropertyValues("videostorm.sources.movies=media/movies")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause().hasMessageContaining("not absolute");
                });
    }
}
