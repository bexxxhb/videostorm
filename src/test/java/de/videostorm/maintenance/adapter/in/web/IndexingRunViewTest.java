package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunStatus;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run view exposes the tally as display-ready fields, including the skipped and missing-data counts
 * the run history columns read.
 */
class IndexingRunViewTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void exposesFoundIndexedSkippedAndMissingDataFromTheRunCounts() {
        IndexingRun run = new IndexingRun(1L, SourceType.MOVIES, RunStatus.COMPLETED,
                T0, T0.plusSeconds(60), new RunCounts(9, 7, 2, 3));

        IndexingRunView view = IndexingRunView.of(run, false);

        assertThat(view.getFound()).isEqualTo(9);
        assertThat(view.getIndexed()).isEqualTo(7);
        assertThat(view.getSkipped()).isEqualTo(2);
        assertThat(view.getMissingData()).isEqualTo(3);
    }
}
