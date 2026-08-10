package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The four extensions that make a file a recognised feature: mkv, wmv, avi and mp4. */
class RecognizedVideoTest {

    @Test
    void recognisesTheFourFeatureExtensionsRegardlessOfCase() {
        assertThat(RecognizedVideo.isVideoFile("feature.mkv")).isTrue();
        assertThat(RecognizedVideo.isVideoFile("feature.WMV")).isTrue();
        assertThat(RecognizedVideo.isVideoFile("Feature.Avi")).isTrue();
        assertThat(RecognizedVideo.isVideoFile("feature.mp4")).isTrue();
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(RecognizedVideo.isVideoFile("movie.mov")).isFalse();
        assertThat(RecognizedVideo.isVideoFile("movie.nfo")).isFalse();
        assertThat(RecognizedVideo.isVideoFile("readme")).isFalse();
        assertThat(RecognizedVideo.isVideoFile("trailer.mkv.part")).isFalse();
        assertThat(RecognizedVideo.isVideoFile(".mkv")).isTrue();
    }
}
