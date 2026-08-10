package de.videostorm.indexing.domain;

/**
 * The lifecycle of a single indexing run.
 *
 * <p>A run is born {@link #RUNNING} and reaches exactly one terminal state: {@link #COMPLETED} when
 * the scan finishes, {@link #FAILED} when it throws, or {@link #INTERRUPTED} when a container
 * restart left it running with no thread to finish it. Only {@link #RUNNING} is active; every other
 * state is a settled record of what happened.
 */
public enum RunStatus {

    RUNNING,
    COMPLETED,
    FAILED,
    INTERRUPTED;

    public boolean isActive() {
        return this == RUNNING;
    }
}
