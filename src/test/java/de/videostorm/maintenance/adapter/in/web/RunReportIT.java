package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.IndexingRunRepository;
import de.videostorm.indexing.application.port.out.RunIssueRepository;
import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunIssue;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The run report through the maintenance page against the full stack: the last run's gaps and a
 * download link are shown, the CSV export carries the run's timestamp and a UTF-8 byte order mark,
 * the endpoint is gated behind login, and a run whose detail has been pruned offers no download.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Sql(statements = {"DELETE FROM indexing_run_issue", "DELETE FROM indexing_run"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RunReportIT extends PostgresIntegrationTestBase {

    private static final Instant T0 = Instant.parse("2026-08-11T09:30:15Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IndexingRunRepository runs;

    @Autowired
    private RunIssueRepository runIssues;

    @Test
    void theMaintenancePageShowsTheLastRunsGapsAndADownloadLink() throws Exception {
        IndexingRun run = completedRun(T0);
        runIssues.record(run.id(), List.of(
                RunIssue.missingField("/media/movies/The Blob", "The Blob", RunIssue.TITLE_FIELD),
                RunIssue.missingField("/media/movies/Heat", "Heat", RunIssue.YEAR_FIELD)));

        String html = render(login(), "/maintenance");

        assertThat(html).contains("Missing title: 1");
        assertThat(html).contains("Missing year: 1");
        assertThat(html).contains("href=\"/maintenance/runs/" + run.id() + "/report.csv\"");
    }

    @Test
    void theCsvDownloadCarriesTheRunTimestampAndAByteOrderMark() throws Exception {
        IndexingRun run = completedRun(T0);
        runIssues.record(run.id(), List.of(
                RunIssue.missingField("/media/movies/The Blob", "The Blob", RunIssue.TITLE_FIELD)));

        byte[] body = mockMvc.perform(get("/maintenance/runs/" + run.id() + "/report.csv").session(login()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"videostorm-run-20260811-093015.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
        String csv = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("type;issue type;path;title;field");
        assertThat(csv).contains("Movies;MISSING_FIELD;/media/movies/The Blob;The Blob;title");
    }

    @Test
    void theDownloadEndpointIsGatedBehindLogin() throws Exception {
        IndexingRun run = completedRun(T0);

        mockMvc.perform(get("/maintenance/runs/" + run.id() + "/report.csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void aRunWhoseDetailHasBeenPrunedOffersNoDownload() throws Exception {
        // Eleven settled runs; detail retained only for the ten most recent.
        IndexingRun oldest = completedRun(T0);
        runIssues.record(oldest.id(), List.of(RunIssue.noVideo("/media/movies/Gone", "Gone")));
        IndexingRun newest = null;
        for (int i = 1; i <= 10; i++) {
            newest = completedRun(T0.plusSeconds(i * 60L));
        }
        runIssues.pruneDetailBeyond(10);

        MockHttpSession session = login();
        mockMvc.perform(get("/maintenance/runs/" + oldest.id() + "/report.csv").session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/maintenance/runs/" + newest.id() + "/report.csv").session(session))
                .andExpect(status().isOk());

        // The pruned run's summary still appears in the history, but offers no download link.
        String html = render(session, "/maintenance");
        assertThat(html).doesNotContain("/maintenance/runs/" + oldest.id() + "/report.csv");
        assertThat(html).contains("/maintenance/runs/" + newest.id() + "/report.csv");
    }

    // Persists a settled run: only one run may be RUNNING at a time, so each is completed at once.
    private IndexingRun completedRun(Instant startedAt) {
        IndexingRun started = runs.save(IndexingRun.start(SourceType.MOVIES, startedAt));
        return runs.save(started.complete(RunCounts.none(), startedAt.plusSeconds(1)));
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/maintenance"))
                .andReturn().getRequest().getSession();
        mockMvc.perform(post("/login")
                .session(session)
                .param("username", ADMIN_USERNAME)
                .param("password", ADMIN_PASSWORD)
                .with(csrf()));
        return session;
    }

    private String render(MockHttpSession session, String path) throws Exception {
        return mockMvc.perform(get(path).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
