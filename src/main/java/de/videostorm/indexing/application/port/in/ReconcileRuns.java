package de.videostorm.indexing.application.port.in;

/**
 * Reconciles run records that a previous JVM left behind. Called once at startup so a run abandoned
 * mid-scan by a container restart is marked interrupted instead of showing as active forever.
 */
public interface ReconcileRuns {

    void markInterruptedRunsFromPreviousLifecycle();
}
