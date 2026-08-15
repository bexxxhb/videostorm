package de.videostorm.maintenance.application.port.in;

import de.videostorm.maintenance.domain.DuplicateScanRun;
import de.videostorm.maintenance.domain.DuplicateScanRunSummary;

import java.util.List;
import java.util.Optional;

/**
 * What the run-result page reads about duplicate scans: the history for its table, and the full groups
 * of a single run for the drill-down layer. The history carries metadata only; the groups are fetched
 * on demand per run so the page render never loads every group of every past scan.
 */
public interface DuplicateScanReports {

    /** Every duplicate scan run, newest first — metadata only, no groups. */
    List<DuplicateScanRunSummary> history();

    /** The full run, groups and members included, or empty when no run has that id. */
    Optional<DuplicateScanRun> findRun(long runId);
}
