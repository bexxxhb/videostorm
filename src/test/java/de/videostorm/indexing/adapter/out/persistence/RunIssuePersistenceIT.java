package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.IndexingRunRepository;
import de.videostorm.indexing.application.port.out.RunIssueRepository;
import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunIssue;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Run issue detail against a real PostgreSQL: a scan's questionable findings are written against the
 * run that produced them, capturing issue type, path, title and field.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Sql(statements = {"DELETE FROM indexing_run_issue", "DELETE FROM indexing_run"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RunIssuePersistenceIT extends PostgresIntegrationTestBase {

    private static final Instant T0 = Instant.parse("2026-08-10T10:00:00Z");

    @Autowired
    private RunIssueRepository runIssues;

    @Autowired
    private IndexingRunRepository runRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void recordsEveryIssueAgainstTheRunCapturingTypePathTitleAndField() {
        IndexingRun run = runRepository.save(IndexingRun.start(SourceType.MOVIES, T0));

        runIssues.record(run.id(), List.of(
                RunIssue.missingField("/media/movies/The Blob", "The Blob", RunIssue.TITLE_FIELD),
                RunIssue.noVideo("/media/movies/Just Metadata", "Stranded"),
                RunIssue.ignoredVideo("/media/movies/Dune/trailer.mp4", "Dune")));

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT issue_type, path, title, field FROM indexing_run_issue WHERE run_id = ? ORDER BY path",
                run.id());
        // Ordered by path: Dune/trailer.mp4, Just Metadata, The Blob.
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("issue_type", "IGNORED_VIDEO")
                .containsEntry("path", "/media/movies/Dune/trailer.mp4")
                .containsEntry("title", "Dune")
                .containsEntry("field", null);
        assertThat(rows.get(1)).containsEntry("issue_type", "NO_VIDEO")
                .containsEntry("title", "Stranded")
                .containsEntry("field", null);
        assertThat(rows.get(2)).containsEntry("issue_type", "MISSING_FIELD")
                .containsEntry("title", "The Blob")
                .containsEntry("field", "title");
    }

    @Test
    void recordingAnEmptyReportWritesNothing() {
        IndexingRun run = runRepository.save(IndexingRun.start(SourceType.MOVIES, T0));

        runIssues.record(run.id(), List.of());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM indexing_run_issue WHERE run_id = ?", Long.class, run.id())).isZero();
    }

    @Test
    void readsBackARunsIssueDetailPreservingNulls() {
        IndexingRun run = runRepository.save(IndexingRun.start(SourceType.MOVIES, T0));
        runIssues.record(run.id(), List.of(
                RunIssue.missingField("/media/movies/The Blob", "The Blob", RunIssue.TITLE_FIELD),
                RunIssue.noVideo("/media/movies/Just Metadata", "Stranded")));

        List<RunIssue> issues = runIssues.findByRun(run.id());

        assertThat(issues).containsExactly(
                RunIssue.missingField("/media/movies/The Blob", "The Blob", RunIssue.TITLE_FIELD),
                RunIssue.noVideo("/media/movies/Just Metadata", "Stranded"));
    }

    @Test
    void prunesDetailBeyondTheRetainedRunsWhileKeepingTheRunSummaries() {
        // Three settled runs, oldest first; retain only the two most recent.
        IndexingRun oldest = completedRun(T0);
        IndexingRun middle = completedRun(T0.plusSeconds(60));
        IndexingRun newest = completedRun(T0.plusSeconds(120));
        runIssues.record(oldest.id(), List.of(RunIssue.noVideo("/media/movies/A", "A")));
        runIssues.record(middle.id(), List.of(RunIssue.noVideo("/media/movies/B", "B")));
        runIssues.record(newest.id(), List.of(RunIssue.noVideo("/media/movies/C", "C")));

        runIssues.pruneDetailBeyond(2);

        assertThat(runIssues.findByRun(oldest.id())).isEmpty();
        assertThat(runIssues.findByRun(middle.id())).hasSize(1);
        assertThat(runIssues.findByRun(newest.id())).hasSize(1);
        // Every run summary survives the prune.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indexing_run", Long.class)).isEqualTo(3L);
    }

    // Persists a settled run: only one run may be RUNNING at a time, so each is completed before
    // the next begins, mirroring the real lifecycle.
    private IndexingRun completedRun(Instant startedAt) {
        IndexingRun started = runRepository.save(IndexingRun.start(SourceType.MOVIES, startedAt));
        return runRepository.save(started.complete(RunCounts.none(), startedAt.plusSeconds(1)));
    }
}
