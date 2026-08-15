package de.videostorm.maintenance.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The outcome of one duplicate-movie scan: when it ran, how long it took, and every duplicate group
 * it found. Persisted in full and retained indefinitely, so the run-result page can replay any past
 * scan. A run with no groups is a valid, meaningful result — it records that a scan found nothing.
 *
 * @param id         the persisted id; {@code null} before the run is stored
 * @param executedAt when the scan ran
 * @param duration   how long the scan took
 * @param groups     the duplicate groups found, possibly empty
 */
public record DuplicateScanRun(Long id, Instant executedAt, Duration duration, List<DuplicateGroup> groups) {

    public DuplicateScanRun {
        groups = List.copyOf(groups);
    }

    /** The number of duplicate groups found — the headline figure the run history shows. */
    public int groupCount() {
        return groups.size();
    }
}
