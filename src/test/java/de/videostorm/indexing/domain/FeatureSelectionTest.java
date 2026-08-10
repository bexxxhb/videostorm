package de.videostorm.indexing.domain;

import de.videostorm.indexing.domain.FeatureSelection.Video;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Choosing the feature among several videos in one folder: the longest normalized common prefix with
 * the folder name wins, the largest file breaks a tie, and everything else is left for the run to
 * record as ignored.
 */
class FeatureSelectionTest {

    @Test
    void aLoneVideoIsTheFeatureWithNothingIgnored() {
        FeatureSelection selection = FeatureSelection.choose("The Thing (1982)",
                List.of(new Video("thing.mkv", 100)));

        assertThat(selection.feature().filename()).isEqualTo("thing.mkv");
        assertThat(selection.ignored()).isEmpty();
    }

    @Test
    void theVideoSharingTheLongestPrefixWithTheFolderIsTheFeature() {
        Video feature = new Video("The Matrix (1999).mkv", 5_000);
        Video trailer = new Video("trailer.mp4", 9_999);

        FeatureSelection selection = FeatureSelection.choose("The Matrix (1999)", List.of(trailer, feature));

        assertThat(selection.feature()).isEqualTo(feature);
        assertThat(selection.ignored()).containsExactly(trailer);
    }

    @Test
    void aTieOnPrefixLengthIsBrokenByTheLargestFile() {
        Video small = new Video("Dune Part One CD1.mkv", 1_000);
        Video large = new Video("Dune Part One CD2.mkv", 8_000);

        FeatureSelection selection = FeatureSelection.choose("Dune Part One", List.of(small, large));

        assertThat(selection.feature()).isEqualTo(large);
        assertThat(selection.ignored()).containsExactly(small);
    }

    @Test
    void ignoresPunctuationAndCaseWhenComparingPrefixes() {
        Video feature = new Video("mad-max-fury-road.mkv", 10);
        Video sample = new Video("sample.mkv", 999);

        FeatureSelection selection = FeatureSelection.choose("Mad Max: Fury Road", List.of(sample, feature));

        assertThat(selection.feature()).isEqualTo(feature);
    }

    @Test
    void refusesToChooseFromNoVideos() {
        assertThatThrownBy(() -> FeatureSelection.choose("Empty", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
