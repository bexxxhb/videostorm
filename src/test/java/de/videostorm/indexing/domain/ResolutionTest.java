package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The movie-side resolution parser: a standalone recognised token in a feature filename is lifted and
 * normalised to the {@code p}-suffixed form. Unlike the show-side {@link TechnicalTokens}, a bare
 * number ({@code 1080}, {@code 720}) is honoured here, because a movie filename has no episode number
 * to collide with.
 */
class ResolutionTest {

    @ParameterizedTest
    @CsvSource({
            "Blade Runner 2049.2160p.mkv, 2160p",
            "Heat.1080p.BluRay.x264.mkv,  1080p",
            "Firefly.720p.mkv,            720p",
    })
    void liftsAPSuffixedTokenUnchanged(String filename, String expected) {
        assertThat(Resolution.fromFilename(filename)).map(Resolution::display).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "The Blob.2160.mkv, 2160p",
            "Heat.1080.mkv,     1080p",
            "Firefly.720.mkv,   720p",
            "Old Film.576.mkv,  576p",
    })
    void addsAPToABareNumberSoTheDisplayAlwaysCarriesIt(String filename, String expected) {
        assertThat(Resolution.fromFilename(filename)).map(Resolution::display).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Some Movie.mkv", "Blade Runner 2049.mkv", "1917.mkv", "Ocean's 480.mkv"})
    void isAbsentWhenNoRecognisedTokenStandsAlone(String filename) {
        assertThat(Resolution.fromFilename(filename)).isEmpty();
    }

    @Test
    void ignoresANumberThatIsMerelyPartOfALongerRun() {
        // 1080 is embedded in 10800, so it is not a standalone token.
        assertThat(Resolution.fromFilename("Movie.10800.mkv")).isEmpty();
    }

    @Test
    void isAbsentForANullFilename() {
        assertThat(Resolution.fromFilename(null)).isEmpty();
    }

    @Test
    void takesTheFirstRecognisedTokenWhenSeveralAppear() {
        assertThat(Resolution.fromFilename("Movie.1080p.remux.720p.mkv"))
                .map(Resolution::display).contains("1080p");
    }
}
