package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The feature-size rule: a directory is only a movie when it holds a recognised video reaching the
 * 500 MB (decimal) threshold, so trailers and samples never stand in for the film.
 */
class FeatureVideoTest {

    @Test
    void theThresholdIsFiveHundredDecimalMegabytes() {
        assertThat(FeatureVideo.MIN_BYTES).isEqualTo(500_000_000L);
    }

    @Test
    void aRecognisedVideoAtOrAboveTheThresholdIsAFeature() {
        assertThat(FeatureVideo.isFeature("Heat.1080p.mkv", FeatureVideo.MIN_BYTES)).isTrue();
        assertThat(FeatureVideo.isFeature("Heat.1080p.mkv", FeatureVideo.MIN_BYTES + 1)).isTrue();
    }

    @Test
    void aRecognisedVideoBelowTheThresholdIsNotAFeature() {
        assertThat(FeatureVideo.isFeature("trailer.mp4", FeatureVideo.MIN_BYTES - 1)).isFalse();
        assertThat(FeatureVideo.isFeature("sample.mkv", 5_000_000L)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes.txt", "poster.jpg", "movie.nfo", "subtitle.srt"})
    void aFileThatIsNotARecognisedVideoIsNeverAFeatureHoweverLarge(String filename) {
        assertThat(FeatureVideo.isFeature(filename, 10L * FeatureVideo.MIN_BYTES)).isFalse();
    }
}
