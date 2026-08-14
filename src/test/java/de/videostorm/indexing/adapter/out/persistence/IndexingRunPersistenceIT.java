package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.in.ReconcileRuns;
import de.videostorm.indexing.application.port.out.IndexingRunRepository;
import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunStatus;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run repository and its migration against a real PostgreSQL: the active-run lookup, the
 * newest-first history window, that history survives (a re-index never deletes it), and that a
 * run left active by a previous lifecycle is reconciled to interrupted at startup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(statements = "DELETE FROM indexing_run", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class IndexingRunPersistenceIT extends PostgresIntegrationTestBase {

    private static final Instant T0 = Instant.parse("2026-08-10T10:00:00Z");

    @Autowired
    private IndexingRunRepository repository;

    @Autowired
    private ReconcileRuns reconcileRuns;

    @Test
    void findsTheSingleActiveRunAndNoneOnceItSettles() {
        IndexingRun active = repository.save(IndexingRun.start(SourceType.MOVIES, T0));
        assertThat(repository.findActiveRun()).isPresent();

        repository.save(active.complete(new RunCounts(3, 2), T0.plusSeconds(60)));

        assertThat(repository.findActiveRun()).isEmpty();
    }

    @Test
    void persistsAndReadsBackTheSkippedCountAlongsideFoundAndIndexed() {
        repository.save(IndexingRun.start(SourceType.MOVIES, T0).complete(new RunCounts(5, 3, 2), T0.plusSeconds(30)));

        IndexingRun stored = repository.findRecent(1).get(0);

        assertThat(stored.counts()).isEqualTo(new RunCounts(5, 3, 2));
    }

    @Test
    void persistsAndReadsBackTheMissingDataCountAlongsideTheRest() {
        repository.save(IndexingRun.start(SourceType.MOVIES, T0)
                .complete(new RunCounts(5, 3, 2, 4), T0.plusSeconds(30)));

        IndexingRun stored = repository.findRecent(1).get(0);

        assertThat(stored.counts()).isEqualTo(new RunCounts(5, 3, 2, 4));
        assertThat(stored.counts().missingData()).isEqualTo(4);
    }

    @Test
    void listsRecentRunsNewestFirstAndCappedAtTheLimit() {
        for (int i = 0; i < 12; i++) {
            repository.save(IndexingRun.start(SourceType.MOVIES, T0.plusSeconds(i))
                    .complete(RunCounts.none(), T0.plusSeconds(i + 1)));
        }

        List<IndexingRun> recent = repository.findRecent(10);

        assertThat(recent).hasSize(10);
        assertThat(recent.get(0).startedAt()).isEqualTo(T0.plusSeconds(11));
        assertThat(recent).isSortedAccordingTo((a, b) -> b.startedAt().compareTo(a.startedAt()));
    }

    @Test
    void aReIndexNeverRemovesEarlierHistory() {
        repository.save(IndexingRun.start(SourceType.MOVIES, T0).complete(RunCounts.none(), T0.plusSeconds(1)));
        repository.save(IndexingRun.start(SourceType.SHOWS, T0.plusSeconds(10)).complete(RunCounts.none(), T0.plusSeconds(11)));

        assertThat(repository.findRecent(10)).hasSize(2);
    }

    @Test
    void marksARunLeftActiveByAPreviousLifecycleAsInterrupted() {
        repository.save(IndexingRun.start(SourceType.SHOWS, T0));

        reconcileRuns.markInterruptedRunsFromPreviousLifecycle();

        assertThat(repository.findActiveRun()).isEmpty();
        assertThat(repository.findRecent(1).get(0).status()).isEqualTo(RunStatus.INTERRUPTED);
    }
}
