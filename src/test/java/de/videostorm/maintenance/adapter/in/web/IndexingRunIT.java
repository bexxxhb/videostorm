package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.indexing.application.port.out.LibraryScan;
import de.videostorm.indexing.application.port.out.MountPreflight;
import de.videostorm.indexing.domain.ScanReport;
import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The run lifecycle through the maintenance page against the full stack: the trigger is gated
 * behind login, a triggered run is recorded and completes, and while a run is active the page
 * shows it, self-refreshes and refuses to start a second one. The scan is gated so the active
 * state can be observed deterministically rather than raced against.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "videostorm.sources.movies=/media/movies")
@Sql(statements = "DELETE FROM indexing_run", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class IndexingRunIT extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatedScan scan;

    @TestConfiguration
    static class GatedScanConfiguration {
        @Bean
        @Primary
        GatedScan gatedScan() {
            return new GatedScan();
        }

        // The configured path does not exist on the test host; this run exercises the lifecycle,
        // not the pre-flight, so report every path reachable and let the trigger through.
        @Bean
        @Primary
        MountPreflight allReachable() {
            return type -> List.of();
        }
    }

    @Test
    void reindexTriggersAreGatedBehindLogin() throws Exception {
        mockMvc.perform(post("/maintenance/movies/reindex").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void triggeringARunRecordsItAndItCompletes() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/maintenance/movies/reindex").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maintenance"));

        String html = pollUntilContains(session, "COMPLETED");
        assertThat(html).contains("Movies");
        assertThat(html).doesNotContain("RUNNING");
        assertThat(countOccurrences(html, "COMPLETED")).isEqualTo(1);
        // The run history carries the Skipped column introduced with size-based movie detection.
        assertThat(html).contains("<th>Skipped</th>");
    }

    @Test
    void whileARunIsActiveThePageShowsItRefreshesAndRefusesASecondTrigger() throws Exception {
        MockHttpSession session = login();
        scan.hold();

        mockMvc.perform(post("/maintenance/movies/reindex").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        scan.awaitEntered();

        String active = render(session);
        assertThat(active).contains("Run in progress");
        assertThat(active).contains("http-equiv=\"refresh\"");
        assertThat(active).containsPattern("<button[^>]*\\bdisabled\\b[^>]*>Re-index movies</button>");

        // A second trigger while active must not start or queue another run.
        mockMvc.perform(post("/maintenance/movies/reindex").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        scan.release();

        String settled = pollUntilContains(session, "COMPLETED");
        assertThat(settled).doesNotContain("Run in progress");
        assertThat(settled).doesNotContain("http-equiv=\"refresh\"");
        assertThat(countOccurrences(settled, "COMPLETED")).isEqualTo(1);
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

    private String render(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/maintenance").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String pollUntilContains(MockHttpSession session, String needle) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            String html = render(session);
            if (html.contains(needle)) {
                return html;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for the page to contain: " + needle);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    /** A scan the test can hold open, so the active state is observable without a race. */
    static final class GatedScan implements LibraryScan {
        private volatile CountDownLatch release = new CountDownLatch(0);
        private volatile CountDownLatch entered = new CountDownLatch(0);

        void hold() {
            release = new CountDownLatch(1);
            entered = new CountDownLatch(1);
        }

        void awaitEntered() throws InterruptedException {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        }

        void release() {
            release.countDown();
        }

        @Override
        public ScanReport scan(SourceType type) {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ScanReport.none();
        }
    }
}
