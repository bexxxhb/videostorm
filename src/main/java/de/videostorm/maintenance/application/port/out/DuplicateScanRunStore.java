package de.videostorm.maintenance.application.port.out;

import de.videostorm.maintenance.domain.DuplicateScanRun;
import de.videostorm.maintenance.domain.DuplicateScanRunSummary;

import java.util.List;
import java.util.Optional;

/**
 * Persists duplicate scan runs and reads them back. Runs are kept indefinitely — nothing here prunes.
 */
public interface DuplicateScanRunStore {

    /** Stores the run with its groups and members, returning it with its assigned id. */
    DuplicateScanRun save(DuplicateScanRun run);

    /** Every stored run as a summary, newest first. */
    List<DuplicateScanRunSummary> history();

    /** The full run with its groups and members, or empty when no run has that id. */
    Optional<DuplicateScanRun> findById(long runId);
}
