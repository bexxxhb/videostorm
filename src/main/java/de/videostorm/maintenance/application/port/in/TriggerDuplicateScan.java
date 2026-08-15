package de.videostorm.maintenance.application.port.in;

import de.videostorm.maintenance.domain.DuplicateScanRun;

/**
 * Runs a duplicate-movie scan now and persists its outcome. A standalone maintenance action — nothing
 * about it is tied to re-indexing. The scan is quick (one catalogue read plus in-memory grouping), so
 * it runs synchronously and returns the stored run.
 */
public interface TriggerDuplicateScan {

    /** Scans the catalogue for duplicate movies, persists the run, and returns it. */
    DuplicateScanRun scan();
}
