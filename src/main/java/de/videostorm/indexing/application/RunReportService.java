package de.videostorm.indexing.application;

import de.videostorm.indexing.application.port.in.RunReportDownload;
import de.videostorm.indexing.application.port.in.RunReports;
import de.videostorm.indexing.application.port.out.IndexingRunRepository;
import de.videostorm.indexing.application.port.out.RunIssueRepository;
import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunGapSummary;
import de.videostorm.indexing.domain.RunIssue;
import de.videostorm.indexing.domain.RunReportCsv;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The read side of the run report: it reports the last run's gaps, tells the page which runs still
 * have detail to download, and builds the CSV for a chosen run.
 *
 * <p>A download is offered only for a run whose detail is still {@link
 * RunIssueRepository#DETAIL_RETENTION_LIMIT retained} — the most recent runs. Anything older keeps
 * its summary but has had its detail pruned, so a request for it is refused rather than served an
 * empty export that looks like a clean run.
 */
@Service
public class RunReportService implements RunReports {

    private static final DateTimeFormatter FILENAME_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final IndexingRunRepository runs;
    private final RunIssueRepository runIssues;

    public RunReportService(IndexingRunRepository runs, RunIssueRepository runIssues) {
        this.runs = runs;
        this.runIssues = runIssues;
    }

    @Override
    public RunGapSummary lastRunGaps() {
        return runs.findRecent(1).stream()
                .findFirst()
                .map(run -> RunGapSummary.from(runIssues.findByRun(run.id())))
                .orElseGet(RunGapSummary::none);
    }

    @Override
    public Set<Long> downloadableRunIds() {
        return retainedRuns().stream()
                .map(IndexingRun::id)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<RunReportDownload> download(long runId) {
        return retainedRuns().stream()
                .filter(run -> run.id() == runId)
                .findFirst()
                .map(this::export);
    }

    private RunReportDownload export(IndexingRun run) {
        List<RunIssue> issues = runIssues.findByRun(run.id());
        byte[] content = RunReportCsv.render(run.type(), issues);
        String filename = "videostorm-run-" + FILENAME_TIMESTAMP.format(run.startedAt()) + ".csv";
        return new RunReportDownload(filename, content);
    }

    private List<IndexingRun> retainedRuns() {
        return runs.findRecent(RunIssueRepository.DETAIL_RETENTION_LIMIT);
    }
}
