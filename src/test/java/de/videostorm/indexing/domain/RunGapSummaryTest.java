package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunGapSummaryTest {

    @Test
    void countsTitleAndYearFieldGapsSeparately() {
        RunGapSummary gaps = RunGapSummary.from(List.of(
                RunIssue.missingField("/m/Blob", "The Blob", RunIssue.TITLE_FIELD),
                RunIssue.missingField("/m/Heat", "Heat", RunIssue.YEAR_FIELD),
                RunIssue.missingField("/m/Dune", "Dune", RunIssue.YEAR_FIELD)));

        assertThat(gaps.titleGaps()).isEqualTo(1);
        assertThat(gaps.yearGaps()).isEqualTo(2);
    }

    @Test
    void ignoresIssuesThatAreNotFieldGaps() {
        RunGapSummary gaps = RunGapSummary.from(List.of(
                RunIssue.noVideo("/m/Just Metadata", "Stranded"),
                RunIssue.ignoredVideo("/m/Dune/trailer.mp4", "Dune"),
                RunIssue.duplicate("/m/Heat (dup)", "Heat", "/m/Heat"),
                RunIssue.skippedEpisode("/s/Show/ep.mkv", "Show")));

        assertThat(gaps.titleGaps()).isZero();
        assertThat(gaps.yearGaps()).isZero();
    }

    @Test
    void noneIsAllZeroes() {
        assertThat(RunGapSummary.from(List.of())).isEqualTo(RunGapSummary.none());
        assertThat(RunGapSummary.none().titleGaps()).isZero();
        assertThat(RunGapSummary.none().yearGaps()).isZero();
    }
}
