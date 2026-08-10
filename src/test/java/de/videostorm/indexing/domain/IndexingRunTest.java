package de.videostorm.indexing.domain;

import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The run lifecycle in isolation: a run starts active and unfinished, reaches exactly one terminal
 * state, and refuses every transition once settled.
 */
class IndexingRunTest {

    private static final Instant START = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant END = Instant.parse("2026-08-10T12:05:00Z");

    @Test
    void startsActiveUnfinishedAndUnpersistedWithNothingCounted() {
        IndexingRun run = IndexingRun.start(SourceType.MOVIES, START);

        assertThat(run.id()).isNull();
        assertThat(run.type()).isEqualTo(SourceType.MOVIES);
        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.isActive()).isTrue();
        assertThat(run.startedAt()).isEqualTo(START);
        assertThat(run.finishedAt()).isNull();
        assertThat(run.counts()).isEqualTo(RunCounts.none());
    }

    @Test
    void completingCapturesCountsAndFinishTime() {
        RunCounts counts = new RunCounts(12, 10);

        IndexingRun completed = IndexingRun.start(SourceType.SHOWS, START).complete(counts, END);

        assertThat(completed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(completed.isActive()).isFalse();
        assertThat(completed.finishedAt()).isEqualTo(END);
        assertThat(completed.counts()).isEqualTo(counts);
    }

    @Test
    void failingRecordsTheFinishTimeAndKeepsNoCounts() {
        IndexingRun failed = IndexingRun.start(SourceType.MOVIES, START).fail(END);

        assertThat(failed.status()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.finishedAt()).isEqualTo(END);
        assertThat(failed.counts()).isEqualTo(RunCounts.none());
    }

    @Test
    void interruptingSettlesTheRunWithoutClaimingItSucceeded() {
        IndexingRun interrupted = IndexingRun.start(SourceType.SHOWS, START).interrupt(END);

        assertThat(interrupted.status()).isEqualTo(RunStatus.INTERRUPTED);
        assertThat(interrupted.isActive()).isFalse();
        assertThat(interrupted.finishedAt()).isEqualTo(END);
    }

    @Test
    void aSettledRunRefusesAnyFurtherTransition() {
        IndexingRun completed = IndexingRun.start(SourceType.MOVIES, START).complete(RunCounts.none(), END);

        assertThatThrownBy(() -> completed.complete(RunCounts.none(), END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED");
        assertThatThrownBy(() -> completed.fail(END)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> completed.interrupt(END)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRunCannotFinishBeforeItStarted() {
        IndexingRun run = IndexingRun.start(SourceType.MOVIES, START);

        assertThatThrownBy(() -> run.complete(RunCounts.none(), START.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingType() {
        assertThatThrownBy(() -> IndexingRun.start(null, START))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
