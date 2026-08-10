package de.videostorm.indexing.application;

import de.videostorm.indexing.application.port.in.IndexingOverview;
import de.videostorm.indexing.application.port.in.IndexingStatus;
import de.videostorm.indexing.application.port.in.ReconcileRuns;
import de.videostorm.indexing.application.port.in.TriggerReindex;
import de.videostorm.indexing.application.port.in.TriggerResult;
import de.videostorm.indexing.application.port.out.CataloguePromotion;
import de.videostorm.indexing.application.port.out.IndexingRunRepository;
import de.videostorm.indexing.application.port.out.LibraryScan;
import de.videostorm.indexing.application.port.out.MountPreflight;
import de.videostorm.indexing.application.port.out.RunIssueRepository;
import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.ScanReport;
import de.videostorm.sources.domain.SourcePath;
import de.videostorm.sources.domain.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Owns the run lifecycle. A trigger persists a {@link IndexingRun} as active and hands the scan to
 * a background {@link Executor}, returning at once; the caller's request never waits on the scan.
 *
 * <p>The single-active invariant is enforced here under a lock: {@link #trigger} refuses if a run
 * is already active rather than submitting a second task to queue behind the first. The database's
 * partial unique index is the backstop should that guard ever be bypassed. Whatever the scan does —
 * complete or throw — the run is always settled, so it can never be stranded {@code RUNNING}.
 *
 * <p>A successful scan is followed by a {@link CataloguePromotion}, swapping the freshly staged
 * catalogue into live in one step. A scan or promotion that throws settles the run {@code FAILED}
 * and leaves the live catalogue exactly as it was.
 *
 * <p>Before any of that, a {@link MountPreflight} check runs under the same lock: if a configured
 * source path is unreachable the trigger aborts without persisting a run or handing anything to the
 * executor, so an unmounted drive can never reach the scan and empty the catalogue. The abort is
 * logged and its failing paths returned to the caller.
 */
@Service
public class IndexingService implements TriggerReindex, IndexingStatus, ReconcileRuns {

    static final int RECENT_LIMIT = 10;

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final IndexingRunRepository repository;
    private final LibraryScan libraryScan;
    private final CataloguePromotion promotion;
    private final MountPreflight mountPreflight;
    private final RunIssueRepository runIssues;
    private final Executor executor;
    private final Clock clock;
    private final Object triggerLock = new Object();

    public IndexingService(IndexingRunRepository repository, LibraryScan libraryScan,
                           CataloguePromotion promotion, MountPreflight mountPreflight,
                           RunIssueRepository runIssues, Executor indexingExecutor, Clock clock) {
        this.repository = repository;
        this.libraryScan = libraryScan;
        this.promotion = promotion;
        this.mountPreflight = mountPreflight;
        this.runIssues = runIssues;
        this.executor = indexingExecutor;
        this.clock = clock;
    }

    @Override
    public TriggerResult trigger(SourceType type) {
        IndexingRun started;
        synchronized (triggerLock) {
            if (repository.findActiveRun().isPresent()) {
                return TriggerResult.alreadyRunning();
            }
            List<SourcePath> unreachable = mountPreflight.unreachable(type);
            if (!unreachable.isEmpty()) {
                List<String> paths = unreachable.stream().map(SourcePath::value).toList();
                log.error("Pre-flight aborted {} re-index: unreachable source paths {}", type, paths);
                return TriggerResult.pathsUnreachable(paths);
            }
            started = repository.save(IndexingRun.start(type, clock.instant()));
        }
        executor.execute(() -> runScan(started));
        return TriggerResult.started();
    }

    private void runScan(IndexingRun run) {
        try {
            ScanReport report = libraryScan.scan(run.type());
            promotion.promote(run.type());
            runIssues.record(run.id(), report.issues());
            repository.save(run.complete(report.counts(), clock.instant()));
        } catch (RuntimeException e) {
            log.error("Indexing run {} for {} failed", run.id(), run.type(), e);
            repository.save(run.fail(clock.instant()));
        }
    }

    @Override
    public IndexingOverview overview() {
        return IndexingOverview.from(repository.findRecent(RECENT_LIMIT));
    }

    @Override
    public void markInterruptedRunsFromPreviousLifecycle() {
        repository.findActiveRun().ifPresent(stranded -> {
            log.warn("Marking run {} for {} interrupted: left running by a previous lifecycle",
                    stranded.id(), stranded.type());
            repository.save(stranded.interrupt(clock.instant()));
        });
    }
}
