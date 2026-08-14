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
    void countsAnEntryMissingBothTitleAndYearOnlyOnce() {
        int missing = RunGapSummary.distinctMissingDataEntries(List.of(
                RunIssue.missingField("/m/Blob", "The Blob", RunIssue.TITLE_FIELD),
                RunIssue.missingField("/m/Blob", "The Blob", RunIssue.YEAR_FIELD),
                RunIssue.missingField("/m/Heat", "Heat", RunIssue.YEAR_FIELD)));

        assertThat(missing).isEqualTo(2);
    }

    @Test
    void distinctMissingDataIgnoresEverySkipDuplicateAndMissingVideoIssue() {
        int missing = RunGapSummary.distinctMissingDataEntries(List.of(
                RunIssue.noVideo("/m/Just Metadata", "Stranded"),
                RunIssue.ignoredVideo("/m/Dune/trailer.mp4", "Dune"),
                RunIssue.duplicate("/m/Heat (dup)", "Heat", "/m/Heat"),
                RunIssue.skippedEpisode("/s/Show/ep.mkv", "Show")));

        assertThat(missing).isZero();
    }

    @Test
    void countsAMissingCastTowardsMissingDataButNotTheTitleOrYearGaps() {
        List<RunIssue> issues = List.of(
                RunIssue.missingField("/m/Heat", "Heat", RunIssue.CAST_FIELD),
                RunIssue.missingField("/m/Dune", "Dune", RunIssue.CAST_FIELD));

        // A missing cast is missing data, so both entries are counted...
        assertThat(RunGapSummary.distinctMissingDataEntries(issues)).isEqualTo(2);
        // ...but cast is not one of the two per-field gaps broken out separately.
        RunGapSummary gaps = RunGapSummary.from(issues);
        assertThat(gaps.titleGaps()).isZero();
        assertThat(gaps.yearGaps()).isZero();
    }

    @Test
    void distinctMissingDataIsZeroWhenThereAreNoIssues() {
        assertThat(RunGapSummary.distinctMissingDataEntries(List.of())).isZero();
    }

    @Test
    void noneIsAllZeroes() {
        assertThat(RunGapSummary.from(List.of())).isEqualTo(RunGapSummary.none());
        assertThat(RunGapSummary.none().titleGaps()).isZero();
        assertThat(RunGapSummary.none().yearGaps()).isZero();
    }
}
