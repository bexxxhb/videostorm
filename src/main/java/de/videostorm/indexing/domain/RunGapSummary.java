package de.videostorm.indexing.domain;

import java.util.List;

/**
 * The per-field gaps a run left behind: how many catalogued entries had a {@link RunIssue#TITLE_FIELD
 * title} that had to be derived from the folder name, and how many had an unknown
 * {@link RunIssue#YEAR_FIELD year}. These are the only two fields the run report tracks as gaps, so
 * only {@link RunIssueType#MISSING_FIELD} issues naming one of them are counted; every other kind of
 * issue is ignored here.
 */
public record RunGapSummary(int titleGaps, int yearGaps) {

    /** No gaps — the summary of a run with nothing thin, or of no run at all. */
    public static RunGapSummary none() {
        return new RunGapSummary(0, 0);
    }

    /** Tallies the title and year field gaps among {@code issues}, ignoring every other issue. */
    public static RunGapSummary from(List<RunIssue> issues) {
        int titleGaps = 0;
        int yearGaps = 0;
        for (RunIssue issue : issues) {
            if (issue.type() != RunIssueType.MISSING_FIELD) {
                continue;
            }
            if (RunIssue.TITLE_FIELD.equals(issue.field())) {
                titleGaps++;
            } else if (RunIssue.YEAR_FIELD.equals(issue.field())) {
                yearGaps++;
            }
        }
        return new RunGapSummary(titleGaps, yearGaps);
    }
}
