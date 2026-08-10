package de.videostorm.indexing.application;

import de.videostorm.indexing.application.port.in.TriggerResult;
import de.videostorm.indexing.application.port.out.IndexingRunRepository;
import de.videostorm.indexing.application.port.out.LibraryScan;
import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunStatus;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run lifecycle around the service, driven by a hand-controlled executor so the background scan
 * runs exactly when the test decides: the single-active guard, immediate return, settling on
 * success and on failure, and startup reconciliation.
 */
class IndexingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    private InMemoryRunRepository repository;
    private ManualExecutor executor;
    private StubScan scan;
    private IndexingService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRunRepository();
        executor = new ManualExecutor();
        scan = new StubScan();
        service = new IndexingService(repository, scan, executor, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void triggerPersistsAnActiveRunAndReturnsBeforeTheScanRuns() {
        TriggerResult result = service.trigger(SourceType.MOVIES);

        assertThat(result).isEqualTo(TriggerResult.STARTED);
        assertThat(executor.pending).hasSize(1);
        assertThat(repository.findActiveRun()).hasValueSatisfying(run -> {
            assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
            assertThat(run.type()).isEqualTo(SourceType.MOVIES);
        });
        assertThat(scan.calls).isZero();
    }

    @Test
    void aSecondTriggerIsRefusedWhileARunIsActiveAndDoesNotQueue() {
        service.trigger(SourceType.MOVIES);

        TriggerResult second = service.trigger(SourceType.SHOWS);

        assertThat(second).isEqualTo(TriggerResult.ALREADY_RUNNING);
        assertThat(executor.pending).hasSize(1);
        assertThat(repository.all).hasSize(1);
    }

    @Test
    void theBackgroundScanCompletesTheRunWithItsCounts() {
        scan.counts = new RunCounts(7, 5);
        service.trigger(SourceType.MOVIES);

        executor.runAll();

        assertThat(repository.findActiveRun()).isEmpty();
        IndexingRun settled = repository.all.get(0);
        assertThat(settled.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(settled.counts()).isEqualTo(new RunCounts(7, 5));
        assertThat(settled.finishedAt()).isEqualTo(NOW);
    }

    @Test
    void aScanThatThrowsSettlesTheRunAsFailedRatherThanLeavingItActive() {
        scan.failure = new IllegalStateException("boom");
        service.trigger(SourceType.MOVIES);

        executor.runAll();

        assertThat(repository.findActiveRun()).isEmpty();
        assertThat(repository.all.get(0).status()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void aRunCanBeStartedAgainOnceThePreviousOneHasSettled() {
        service.trigger(SourceType.MOVIES);
        executor.runAll();

        TriggerResult again = service.trigger(SourceType.SHOWS);

        assertThat(again).isEqualTo(TriggerResult.STARTED);
        assertThat(repository.all).hasSize(2);
    }

    @Test
    void recentRunsAskTheRepositoryForTheLastTen() {
        service.recentRuns();

        assertThat(repository.lastRequestedLimit).isEqualTo(10);
    }

    @Test
    void reconciliationInterruptsARunLeftActiveByAPreviousLifecycle() {
        repository.save(IndexingRun.start(SourceType.MOVIES, NOW.minusSeconds(3600)));

        service.markInterruptedRunsFromPreviousLifecycle();

        assertThat(repository.findActiveRun()).isEmpty();
        assertThat(repository.all.get(0).status()).isEqualTo(RunStatus.INTERRUPTED);
    }

    @Test
    void reconciliationIsANoOpWhenNothingWasLeftActive() {
        service.markInterruptedRunsFromPreviousLifecycle();

        assertThat(repository.all).isEmpty();
    }

    private static final class ManualExecutor implements Executor {
        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        void runAll() {
            List<Runnable> toRun = List.copyOf(pending);
            pending.clear();
            toRun.forEach(Runnable::run);
        }
    }

    private static final class StubScan implements LibraryScan {
        private int calls;
        private RunCounts counts = RunCounts.none();
        private RuntimeException failure;

        @Override
        public RunCounts scan(SourceType type) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return counts;
        }
    }

    /** Stands in for the JPA-backed repository, tracking the single-active invariant by id. */
    private static final class InMemoryRunRepository implements IndexingRunRepository {
        private final List<IndexingRun> all = new ArrayList<>();
        private long sequence;
        private int lastRequestedLimit;

        @Override
        public IndexingRun save(IndexingRun run) {
            if (run.id() == null) {
                IndexingRun persisted = withId(run, ++sequence);
                all.add(persisted);
                return persisted;
            }
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equals(run.id())) {
                    all.set(i, run);
                    return run;
                }
            }
            all.add(run);
            return run;
        }

        @Override
        public Optional<IndexingRun> findActiveRun() {
            return all.stream().filter(IndexingRun::isActive).findFirst();
        }

        @Override
        public List<IndexingRun> findRecent(int limit) {
            lastRequestedLimit = limit;
            return List.copyOf(all);
        }

        private static IndexingRun withId(IndexingRun run, long id) {
            return new IndexingRun(id, run.type(), run.status(), run.startedAt(),
                    run.finishedAt(), run.counts());
        }
    }
}
