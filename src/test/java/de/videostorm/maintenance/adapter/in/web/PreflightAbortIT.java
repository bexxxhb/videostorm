package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pre-flight mount validation through the full stack: a configured movie path that no drive backs
 * is unreachable, so triggering a re-index must abort before anything is persisted or written. The
 * operator is redirected — so a refresh cannot re-trigger — and told which path failed, the
 * catalogue history stays empty because no run was recorded, and the error survives exactly one
 * render.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "videostorm.sources.movies=/nonexistent-videostorm-preflight-mount")
@Sql(statements = "DELETE FROM indexing_run", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PreflightAbortIT extends PostgresIntegrationTestBase {

    private static final String UNREACHABLE_PATH = "/nonexistent-videostorm-preflight-mount";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void triggeringWithAnUnreachablePathAbortsRedirectsNamesThePathAndPersistsNoRun() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/maintenance/movies/reindex").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maintenance"));

        String afterAbort = render(session);
        assertThat(afterAbort).contains("Re-index aborted");
        assertThat(afterAbort).contains(UNREACHABLE_PATH);
        // No run was persisted, so the history is still empty and nothing shows as running.
        assertThat(afterAbort).contains("No runs yet.");
        assertThat(afterAbort).doesNotContain("RUNNING");
    }

    @Test
    void theAbortErrorSurvivesExactlyOneRenderSoARefreshDoesNotRepeatIt() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/maintenance/movies/reindex").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(render(session)).contains("Re-index aborted");
        // A refresh is a fresh GET with no re-POST: the flashed error is gone and nothing re-runs.
        assertThat(render(session)).doesNotContain("Re-index aborted");
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
}
