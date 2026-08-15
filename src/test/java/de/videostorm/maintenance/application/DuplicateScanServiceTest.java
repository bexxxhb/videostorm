package de.videostorm.maintenance.application;

import de.videostorm.maintenance.application.port.out.DuplicateScanCandidates;
import de.videostorm.maintenance.application.port.out.DuplicateScanRunStore;
import de.videostorm.maintenance.domain.DuplicateCriterion;
import de.videostorm.maintenance.domain.DuplicateScanRun;
import de.videostorm.maintenance.domain.DuplicateScanRunSummary;
import de.videostorm.maintenance.domain.ScanCandidate;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service wires the scan together: it reads candidates, groups them, stamps the run with the
 * clock's instant, and persists it. Reads pass straight through to the store.
 */
class DuplicateScanServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T10:15:30Z");

    private final DuplicateScanCandidates candidates = () -> List.of(
            ScanCandidate.of("tt1", "The Matrix", "/a"),
            ScanCandidate.of("tt1", "Matrix", "/b"));
    private final RecordingStore store = new RecordingStore();
    private final DuplicateScanService service =
            new DuplicateScanService(candidates, store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void scansGroupsStampsAndPersistsTheRun() {
        DuplicateScanRun run = service.scan();

        assertThat(run.id()).isNotNull();
        assertThat(run.executedAt()).isEqualTo(NOW);
        assertThat(run.duration()).isNotNull();
        assertThat(run.duration().isNegative()).isFalse();
        assertThat(run.groups()).singleElement().satisfies(group -> {
            assertThat(group.criterion()).isEqualTo(DuplicateCriterion.IMDB_ID);
            assertThat(group.members()).hasSize(2);
        });
        assertThat(store.saved).hasSize(1);
    }

    @Test
    void historyAndFindRunDelegateToTheStore() {
        DuplicateScanRun saved = service.scan();

        assertThat(service.history()).singleElement()
                .satisfies(summary -> assertThat(summary.groupCount()).isEqualTo(1));
        assertThat(service.findRun(saved.id())).contains(saved);
        assertThat(service.findRun(999L)).isEmpty();
    }

    private static final class RecordingStore implements DuplicateScanRunStore {

        private final List<DuplicateScanRun> saved = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public DuplicateScanRun save(DuplicateScanRun run) {
            DuplicateScanRun stored = new DuplicateScanRun(
                    ids.incrementAndGet(), run.executedAt(), run.duration(), run.groups());
            saved.add(stored);
            return stored;
        }

        @Override
        public List<DuplicateScanRunSummary> history() {
            return saved.stream()
                    .map(run -> new DuplicateScanRunSummary(
                            run.id(), run.executedAt(), run.duration(), run.groupCount()))
                    .toList();
        }

        @Override
        public Optional<DuplicateScanRun> findById(long runId) {
            return saved.stream().filter(run -> run.id() == runId).findFirst();
        }
    }
}
