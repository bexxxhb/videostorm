package de.videostorm.indexing.application.port.in;

import de.videostorm.indexing.domain.IndexingRun;

import java.util.List;
import java.util.Optional;

/**
 * One consistent snapshot of indexing status: the whole run history, newest first, and the active run
 * derived from it.
 *
 * <p>The active run is the newest {@link IndexingRun#isActive() active} run in the snapshot. The
 * single-active invariant — a service lock backed by a partial unique index — guarantees at most one
 * is active, and because an active run is always the most recently started it is always present in
 * the history. Deriving both from a single read is what lets a caller render the active run and
 * the history without them disagreeing, which two separate reads could not guarantee: a background
 * scan settling between them would show the run as active and completed at once.
 */
public record IndexingOverview(Optional<IndexingRun> activeRun, List<IndexingRun> history) {

    /** Derives the active run from {@code history}, treating that list as the whole snapshot. */
    public static IndexingOverview from(List<IndexingRun> history) {
        Optional<IndexingRun> activeRun = history.stream()
                .filter(IndexingRun::isActive)
                .findFirst();
        return new IndexingOverview(activeRun, history);
    }
}
