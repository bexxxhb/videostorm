package de.videostorm.indexing.application;

import de.videostorm.indexing.application.port.in.RunReportDownload;
import de.videostorm.indexing.application.port.out.IndexingRunRepository;
import de.videostorm.indexing.application.port.out.RunIssueRepository;
import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunIssue;
import de.videostorm.indexing.domain.RunStatus;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read side of the run report against in-memory fakes: the last run's gaps, which runs are still
 * downloadable, and the CSV export — including that a run outside the retention window is refused
 * rather than served an export.
 */
class RunReportServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-11T09:30:15Z");

    private StubRunRepository runs;
    private StubRunIssues runIssues;
    private RunReportService service;

    @BeforeEach
    void setUp() {
        runs = new StubRunRepository();
        runIssues = new StubRunIssues();
        service = new RunReportService(runs, runIssues);
    }

    @Test
    void theLastRunGapsCountItsTitleAndYearFieldGaps() {
        IndexingRun run = runs.add(1L, SourceType.MOVIES, T0);
        runIssues.put(run.id(), List.of(
                RunIssue.missingField("/m/Blob", "The Blob", RunIssue.TITLE_FIELD),
                RunIssue.missingField("/m/Heat", "Heat", RunIssue.YEAR_FIELD),
                RunIssue.noVideo("/m/Empty", "Empty")));

        assertThat(service.lastRunGaps().titleGaps()).isEqualTo(1);
        assertThat(service.lastRunGaps().yearGaps()).isEqualTo(1);
    }

    @Test
    void withNoRunsThereAreNoGaps() {
        assertThat(service.lastRunGaps().titleGaps()).isZero();
        assertThat(service.lastRunGaps().yearGaps()).isZero();
    }

    @Test
    void theRetainedRunsAreTheOnesOfferedForDownload() {
        runs.add(1L, SourceType.MOVIES, T0);
        runs.add(2L, SourceType.SHOWS, T0.plusSeconds(60));

        assertThat(service.downloadableRunIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void aRetainedRunExportsCsvBytesUnderAFilenameCarryingItsTimestamp() {
        IndexingRun run = runs.add(7L, SourceType.MOVIES, T0);
        runIssues.put(run.id(), List.of(
                RunIssue.missingField("/m/Blob", "The Blob", RunIssue.TITLE_FIELD)));

        Optional<RunReportDownload> download = service.download(7L);

        assertThat(download).isPresent();
        assertThat(download.get().filename()).isEqualTo("videostorm-run-20260811-093015.csv");
        String csv = new String(download.get().content(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿type;issue type;path;title;field");
        assertThat(csv).contains("Movies;MISSING_FIELD;/m/Blob;The Blob;title");
    }

    @Test
    void aRunOutsideTheRetentionWindowIsNotDownloadable() {
        runs.retained = List.of(); // nothing retained: every run's detail has been pruned
        runs.add(3L, SourceType.MOVIES, T0);

        assertThat(service.download(3L)).isEmpty();
        assertThat(service.downloadableRunIds()).isEmpty();
    }

    /** Serves a fixed retained window (defaulting to every added run) and honours the requested limit. */
    private static final class StubRunRepository implements IndexingRunRepository {
        private final List<IndexingRun> all = new ArrayList<>();
        private List<IndexingRun> retained; // null => all runs are retained

        IndexingRun add(long id, SourceType type, Instant startedAt) {
            IndexingRun run = new IndexingRun(id, type, RunStatus.COMPLETED, startedAt,
                    startedAt.plusSeconds(1), RunCounts.none());
            all.add(run);
            return run;
        }

        @Override
        public IndexingRun save(IndexingRun run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IndexingRun> findActiveRun() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IndexingRun> findRecent(int limit) {
            List<IndexingRun> source = retained != null ? retained : all;
            return source.stream().limit(limit).toList();
        }

        @Override
        public List<IndexingRun> findAll() {
            return List.copyOf(all);
        }
    }

    private static final class StubRunIssues implements RunIssueRepository {
        private final Map<Long, List<RunIssue>> byRun = new HashMap<>();

        void put(long runId, List<RunIssue> issues) {
            byRun.put(runId, issues);
        }

        @Override
        public void record(long runId, List<RunIssue> issues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RunIssue> findByRun(long runId) {
            return byRun.getOrDefault(runId, List.of());
        }

        @Override
        public void pruneDetailBeyond(int retainedRuns) {
            throw new UnsupportedOperationException();
        }
    }
}
