package de.videostorm.indexing.domain;

import de.videostorm.sources.domain.SourceType;

import java.time.Instant;

/**
 * A single indexing run: one attempt to rebuild the catalogue for one {@link SourceType}.
 *
 * <p>The aggregate is immutable — every lifecycle transition returns a new instance and refuses to
 * fire unless the run is still {@link RunStatus#RUNNING RUNNING}, so a run can never be completed
 * twice or resurrected from a settled state. A run left {@code RUNNING} with no thread behind it
 * (a container restart mid-scan) is reconciled to {@link RunStatus#INTERRUPTED} at startup rather
 * than lying about being active forever.
 *
 * <p>{@link #id()} is {@code null} until the run has been persisted.
 */
public record IndexingRun(
        Long id,
        SourceType type,
        RunStatus status,
        Instant startedAt,
        Instant finishedAt,
        RunCounts counts) {

    public IndexingRun {
        if (type == null) {
            throw new IllegalArgumentException("Run type must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Run status must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("Run start time must not be null");
        }
        if (counts == null) {
            throw new IllegalArgumentException("Run counts must not be null");
        }
    }

    /** Begins a run for {@code type} at {@code startedAt}: active, unfinished, nothing counted yet. */
    public static IndexingRun start(SourceType type, Instant startedAt) {
        return new IndexingRun(null, type, RunStatus.RUNNING, startedAt, null, RunCounts.none());
    }

    /** Records a successful scan, capturing what it tallied and when it finished. */
    public IndexingRun complete(RunCounts finalCounts, Instant finishedAt) {
        return settle(RunStatus.COMPLETED, finishedAt, finalCounts);
    }

    /** Records a scan that threw: no counts survive, only the fact that it ended and when. */
    public IndexingRun fail(Instant finishedAt) {
        return settle(RunStatus.FAILED, finishedAt, RunCounts.none());
    }

    /** Marks a run that a restart abandoned mid-scan, so the page stops showing it as active. */
    public IndexingRun interrupt(Instant finishedAt) {
        return settle(RunStatus.INTERRUPTED, finishedAt, counts);
    }

    public boolean isActive() {
        return status.isActive();
    }

    private IndexingRun settle(RunStatus terminal, Instant finishedAt, RunCounts finalCounts) {
        if (!status.isActive()) {
            throw new IllegalStateException(
                    "Run is already " + status + " and cannot become " + terminal);
        }
        if (finishedAt == null) {
            throw new IllegalArgumentException("Run finish time must not be null");
        }
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Run cannot finish before it started");
        }
        return new IndexingRun(id, type, terminal, startedAt, finishedAt, finalCounts);
    }
}
