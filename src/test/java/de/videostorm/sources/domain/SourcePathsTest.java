package de.videostorm.sources.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage of the configured set as a whole: grouping by type, the empty-type allowance, and the
 * two overlap faults — a duplicate path and a nested path — each of which must be rejected while
 * naming the offending pair.
 */
class SourcePathsTest {

    @Test
    void groupsNormalizedPathsByType() {
        SourcePaths paths = SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of(" /media/movies/ "),
                SourceType.SHOWS, List.of("/media/shows")));

        assertThat(paths.pathsFor(SourceType.MOVIES)).containsExactly(SourcePath.of("/media/movies"));
        assertThat(paths.pathsFor(SourceType.SHOWS)).containsExactly(SourcePath.of("/media/shows"));
        assertThat(paths.hasPathsFor(SourceType.MOVIES)).isTrue();
        assertThat(paths.hasPathsFor(SourceType.SHOWS)).isTrue();
    }

    @Test
    void aTypeWithNoConfiguredPathsIsAllowedAndReportsAsUnconfigured() {
        SourcePaths paths = SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of("/media/movies")));

        assertThat(paths.hasPathsFor(SourceType.SHOWS)).isFalse();
        assertThat(paths.pathsFor(SourceType.SHOWS)).isEmpty();
    }

    @Test
    void anEmptyConfigurationIsAllowed() {
        SourcePaths paths = SourcePaths.fromRaw(Map.of());

        assertThat(paths.hasPathsFor(SourceType.MOVIES)).isFalse();
        assertThat(paths.hasPathsFor(SourceType.SHOWS)).isFalse();
    }

    @Test
    void rejectsAPathThatAppearsTwiceNamingIt() {
        assertThatThrownBy(() -> SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of("/media/movies", "/media/movies/"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appears twice")
                .hasMessageContaining("/media/movies");
    }

    @Test
    void rejectsANestedPathNamingBothSides() {
        assertThatThrownBy(() -> SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of("/media", "/media/movies"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap")
                .hasMessageContaining("/media")
                .hasMessageContaining("/media/movies");
    }

    @Test
    void rejectsTheFilesystemRootAsOverlappingAnyOtherPath() {
        assertThatThrownBy(() -> SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of("/"),
                SourceType.SHOWS, List.of("/media/shows"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void detectsOverlapAcrossDifferentTypes() {
        assertThatThrownBy(() -> SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of("/media/library"),
                SourceType.SHOWS, List.of("/media/library/shows"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void acceptsDistinctNonNestedPaths() {
        SourcePaths paths = SourcePaths.fromRaw(Map.of(
                SourceType.MOVIES, List.of("/media/movies", "/mnt/films"),
                SourceType.SHOWS, List.of("/media/shows")));

        assertThat(paths.pathsFor(SourceType.MOVIES)).hasSize(2);
        assertThat(paths.pathsFor(SourceType.SHOWS)).hasSize(1);
    }
}
