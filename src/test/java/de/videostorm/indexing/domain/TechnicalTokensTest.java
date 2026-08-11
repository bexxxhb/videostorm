package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stripping rules applied before an episode number is matched: extension and technical tokens go,
 * a bare resolution number stays. Asserted on token presence rather than an exact cleaned string, so
 * the tests do not pin the incidental separators the removal leaves behind.
 */
class TechnicalTokensTest {

    @ParameterizedTest
    @ValueSource(strings = {"mkv", "mp4", "avi", "wmv"})
    void stripsTheFileExtension(String extension) {
        assertThat(TechnicalTokens.strip("Show.S01E01." + extension)).doesNotContain(extension);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1080p", "720p", "2160p", "1080i", "480P"})
    void stripsAResolutionTokenWithAPorISuffix(String resolution) {
        assertThat(TechnicalTokens.strip("Show.S01E01." + resolution + ".mkv"))
                .doesNotContainIgnoringCase(resolution);
    }

    @ParameterizedTest
    @ValueSource(strings = {"720", "1080", "2160"})
    void keepsABareResolutionNumberWithNoSuffix(String bare) {
        // Indistinguishable from the three-digit episode form on the show side, so it must survive.
        assertThat(TechnicalTokens.strip("Show." + bare + ".mkv")).contains(bare);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "x264", "x265", "h264", "h265", "HEVC", "AVC", "AAC", "AC3", "DTS",
            "BluRay", "BDRip", "WEB-DL", "WEBRip", "HDTV", "REMUX", "PROPER", "10bit", "HDR"})
    void stripsEveryFixedCodecSourceAndAudioToken(String token) {
        assertThat(TechnicalTokens.strip("Show.S01E01." + token + ".mkv"))
                .doesNotContainIgnoringCase(token);
    }

    @Test
    void matchesTokensCaseInsensitively() {
        assertThat(TechnicalTokens.strip("Show.bluray.x264.mkv")).doesNotContainIgnoringCase("bluray");
    }

    @ParameterizedTest
    @CsvSource({
            "The DTShow.mkv,     DTShow",
            "Bladerunner.mkv,    Bladerunner",
    })
    void leavesATokenThatIsMerelyASubstringOfATitleWordAlone(String filename, String survivingWord) {
        assertThat(TechnicalTokens.strip(filename)).contains(survivingWord);
    }

    @Test
    void keepsTheSeasonAndEpisodeMarkerThatSitsBetweenStrippedTokens() {
        assertThat(TechnicalTokens.strip("Firefly.720p.S01E01.x264.mkv")).contains("S01E01");
    }
}
