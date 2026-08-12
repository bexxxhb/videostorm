package de.videostorm.indexing.application.port.in;

import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunStatus;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule that turns a recent-runs snapshot into an overview: the active run is the newest
 * still-running run in the snapshot, and the history is passed through untouched.
 */
class IndexingOverviewTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void hasNoActiveRunWhenTheSnapshotIsEmpty() {
        IndexingOverview overview = IndexingOverview.from(List.of());

        assertThat(overview.activeRun()).isEmpty();
        assertThat(overview.history()).isEmpty();
    }

    @Test
    void hasNoActiveRunWhenEveryRunHasSettled() {
        IndexingOverview overview = IndexingOverview.from(List.of(
                run(2, RunStatus.COMPLETED),
                run(1, RunStatus.FAILED)));

        assertThat(overview.activeRun()).isEmpty();
    }

    @Test
    void picksTheRunningRunAsActive() {
        IndexingRun running = run(2, RunStatus.RUNNING);

        IndexingOverview overview = IndexingOverview.from(List.of(
                running,
                run(1, RunStatus.COMPLETED)));

        assertThat(overview.activeRun()).contains(running);
    }

    @Test
    void passesTheRecentHistoryThroughUnchanged() {
        List<IndexingRun> recent = List.of(run(2, RunStatus.RUNNING), run(1, RunStatus.COMPLETED));

        IndexingOverview overview = IndexingOverview.from(recent);

        assertThat(overview.history()).isEqualTo(recent);
    }

    private static IndexingRun run(long id, RunStatus status) {
        Instant finishedAt = status.isActive() ? null : NOW;
        return new IndexingRun(id, SourceType.MOVIES, status, NOW.minusSeconds(id), finishedAt,
                RunCounts.none());
    }
}
