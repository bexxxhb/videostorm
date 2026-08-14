package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.config.PugViewConfiguration;
import de.videostorm.config.security.AdminUserDetailsService;
import de.videostorm.config.security.SecurityConfig;
import de.videostorm.indexing.application.port.in.IndexingOverview;
import de.videostorm.indexing.application.port.in.IndexingStatus;
import de.videostorm.indexing.application.port.in.RunReports;
import de.videostorm.indexing.application.port.in.TriggerReindex;
import de.videostorm.indexing.domain.RunGapSummary;
import de.videostorm.sources.config.SourcesConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast, DB-free coverage of the maintenance area's authentication gate: the saved-request
 * redirect flow, the unlinked login page, logout, and the CSRF-protected, inert re-index
 * triggers. {@link MaintenanceController} and {@link LoginController} have no database
 * dependency, so the full {@link SecurityConfig} filter chain is exercised here directly.
 */
@WebMvcTest(controllers = {MaintenanceController.class, LoginController.class})
@Import({SecurityConfig.class, AdminUserDetailsService.class, PugViewConfiguration.class,
        SourcesConfiguration.class})
@TestPropertySource(properties = {
        "application.operating.mode=maintenance",
        "videostorm.admin.username=" + PostgresIntegrationTestBase.ADMIN_USERNAME,
        "videostorm.admin.password=" + PostgresIntegrationTestBase.ADMIN_PASSWORD
})
class MaintenanceSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TriggerReindex triggerReindex;

    @MockitoBean
    private IndexingStatus indexingStatus;

    @MockitoBean
    private RunReports runReports;

    @BeforeEach
    void noRunsByDefault() {
        Mockito.when(indexingStatus.overview()).thenReturn(IndexingOverview.from(List.of()));
        Mockito.when(runReports.lastRunGaps()).thenReturn(RunGapSummary.none());
        Mockito.when(runReports.downloadableRunIds()).thenReturn(java.util.Set.of());
    }

    @Test
    void unauthenticatedRequestsToMaintenanceRedirectToLogin() throws Exception {
        mockMvc.perform(get("/maintenance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void theLoginPageIsReachableDirectlyWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void staticJavaScriptIsServedPubliclySoThePlotDialogWiresUp() throws Exception {
        // The plot/raw-data dialog only opens if /js/content-dialog.js loads; without /js/** permitted
        // the filter chain redirects the unauthenticated request to /login and the script never runs.
        mockMvc.perform(get("/js/content-dialog.js")).andExpect(status().isOk());
    }

    @Test
    void successfulLoginLandsBackOnTheOriginallyRequestedMaintenancePage() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/maintenance"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();

        mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", PostgresIntegrationTestBase.ADMIN_USERNAME)
                        .param("password", PostgresIntegrationTestBase.ADMIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/maintenance*"));
    }

    @Test
    void loggingOutRedirectsToThePublicListing() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user(PostgresIntegrationTestBase.ADMIN_USERNAME).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void maintenancePageRendersBothReindexTriggersInertWithCsrfTokens() throws Exception {
        String html = mockMvc.perform(get("/maintenance")
                        .with(user(PostgresIntegrationTestBase.ADMIN_USERNAME).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("action=\"/maintenance/movies/reindex\"");
        assertThat(html).contains("action=\"/maintenance/shows/reindex\"");
        assertThat(html).containsPattern("<button[^>]*\\bdisabled\\b[^>]*>Re-index movies</button>");
        assertThat(html).containsPattern("<button[^>]*\\bdisabled\\b[^>]*>Re-index shows</button>");
        assertThat(countOccurrences(html, "type=\"hidden\"")).isEqualTo(3);
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
}
