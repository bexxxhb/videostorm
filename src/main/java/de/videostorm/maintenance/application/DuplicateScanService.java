package de.videostorm.maintenance.application;

import de.videostorm.maintenance.application.port.in.DuplicateScanReports;
import de.videostorm.maintenance.application.port.in.TriggerDuplicateScan;
import de.videostorm.maintenance.application.port.out.DuplicateScanCandidates;
import de.videostorm.maintenance.application.port.out.DuplicateScanRunStore;
import de.videostorm.maintenance.domain.DuplicateGroup;
import de.videostorm.maintenance.domain.DuplicateScanRun;
import de.videostorm.maintenance.domain.DuplicateScanRunSummary;
import de.videostorm.maintenance.domain.DuplicateScanner;
import de.videostorm.maintenance.domain.ScanCandidate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Drives a duplicate scan end to end and answers the run-result page's reads. Triggering reads the
 * catalogue, groups it in memory, times the work, and stores the run; the reads pass straight through
 * to the store. The scanner itself is pure domain logic held here rather than wired as a bean.
 */
@Service
public class DuplicateScanService implements TriggerDuplicateScan, DuplicateScanReports {

    private final DuplicateScanCandidates candidates;
    private final DuplicateScanRunStore store;
    private final Clock clock;
    private final DuplicateScanner scanner = new DuplicateScanner();

    public DuplicateScanService(DuplicateScanCandidates candidates, DuplicateScanRunStore store, Clock clock) {
        this.candidates = candidates;
        this.store = store;
        this.clock = clock;
    }

    @Override
    public DuplicateScanRun scan() {
        Instant startedAt = clock.instant();
        long startNanos = System.nanoTime();

        List<ScanCandidate> movies = candidates.all();
        List<DuplicateGroup> groups = scanner.scan(movies);

        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        return store.save(new DuplicateScanRun(null, startedAt, duration, groups));
    }

    @Override
    public List<DuplicateScanRunSummary> history() {
        return store.history();
    }

    @Override
    public Optional<DuplicateScanRun> findRun(long runId) {
        return store.findById(runId);
    }
}
