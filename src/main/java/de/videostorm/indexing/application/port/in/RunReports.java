package de.videostorm.indexing.application.port.in;

import de.videostorm.indexing.domain.RunGapSummary;

import java.util.Optional;
import java.util.Set;

/**
 * What the maintenance page reads about a run's issue detail: the gaps the last run left, which runs
 * still have detail to download, and the CSV export for a chosen run.
 *
 * <p>Detail is retained only for the most recent runs; older runs keep their summary in the history
 * but no longer offer a download.
 */
public interface RunReports {

    /** The title and year gaps of the most recent run; {@link RunGapSummary#none()} when there is none. */
    RunGapSummary lastRunGaps();

    /** The ids of the runs whose detail is still retained, so a download can be offered for them. */
    Set<Long> downloadableRunIds();

    /** The CSV export for {@code runId}, or empty when that run's detail has been pruned or never existed. */
    Optional<RunReportDownload> download(long runId);
}
