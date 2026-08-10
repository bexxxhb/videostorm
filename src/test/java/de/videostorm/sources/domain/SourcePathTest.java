package de.videostorm.sources.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure coverage of a single source path's normalization and nesting rules: whitespace is
 * trimmed, the path must be absolute, trailing slashes are stripped, and a strict ancestor is
 * distinguished from an equal or merely string-prefixed path.
 */
class SourcePathTest {

    @ParameterizedTest
    @CsvSource({
            "'  /media/movies  ', /media/movies",
            "'/media/movies/',    /media/movies",
            "'/media/movies///',  /media/movies",
            "'/',                 /",
            "'/media/movies',     /media/movies"
    })
    void trimsWhitespaceAndStripsTrailingSlashes(String raw, String expected) {
        assertThat(SourcePath.of(raw).value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"media/movies", "./movies", "../movies", "C:/movies"})
    void rejectsPathsThatAreNotAbsolute(String raw) {
        assertThatThrownBy(() -> SourcePath.of(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not absolute")
                .hasMessageContaining(raw.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankPaths(String raw) {
        assertThatThrownBy(() -> SourcePath.of(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> SourcePath.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizedPathsWithEquivalentTrailingSlashesAreEqual() {
        assertThat(SourcePath.of("/media/movies/")).isEqualTo(SourcePath.of("/media/movies"));
        assertThat(SourcePath.of("/media/movies/").hashCode())
                .isEqualTo(SourcePath.of("/media/movies").hashCode());
    }

    @Test
    void aParentIsAStrictPrefixOfItsDescendant() {
        assertThat(SourcePath.of("/media").isPrefixOf(SourcePath.of("/media/movies"))).isTrue();
    }

    @Test
    void anEqualPathIsNotAPrefix() {
        assertThat(SourcePath.of("/media/movies").isPrefixOf(SourcePath.of("/media/movies")))
                .isFalse();
    }

    @Test
    void aSiblingSharingAStringPrefixButNotASegmentIsNotAPrefix() {
        assertThat(SourcePath.of("/media/mov").isPrefixOf(SourcePath.of("/media/movies")))
                .isFalse();
    }

    @Test
    void theFilesystemRootIsAPrefixOfEveryOtherPath() {
        assertThat(SourcePath.of("/").isPrefixOf(SourcePath.of("/media/movies"))).isTrue();
        assertThat(SourcePath.of("/").isPrefixOf(SourcePath.of("/"))).isFalse();
    }
}
