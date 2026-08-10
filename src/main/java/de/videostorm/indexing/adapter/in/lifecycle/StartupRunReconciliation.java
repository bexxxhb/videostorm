package de.videostorm.indexing.adapter.in.lifecycle;

import de.videostorm.indexing.application.port.in.ReconcileRuns;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Once the context is up, reconciles any run a previous JVM left active. This runs before the
 * maintenance area can accept a trigger — those arrive over HTTP, after the application is ready —
 * so a run stranded by a container restart is settled as interrupted before a new one can start.
 */
@Component
class StartupRunReconciliation {

    private final ReconcileRuns reconcileRuns;

    StartupRunReconciliation(ReconcileRuns reconcileRuns) {
        this.reconcileRuns = reconcileRuns;
    }

    @EventListener(ApplicationReadyEvent.class)
    void reconcile() {
        reconcileRuns.markInterruptedRunsFromPreviousLifecycle();
    }
}
