package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the maintenance shell against the real security filter chain wired
 * into the full application context: the header link, the unlinked login page, the
 * saved-request round trip and that the catalogue stays public once security is in the mix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MaintenanceIT extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listingRoutesAndStylesheetsRemainReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/movies")).andExpect(status().isOk());
        mockMvc.perform(get("/shows")).andExpect(status().isOk());
        mockMvc.perform(get("/css/videostorm.css")).andExpect(status().isOk());
    }

    @Test
    void maintenanceLinkAppearsInTheHeaderOfEveryPublicPage() throws Exception {
        assertThat(render("/movies")).contains("href=\"/maintenance\"");
        assertThat(render("/shows")).contains("href=\"/maintenance\"");
    }

    @Test
    void theLoginPageIsNotLinkedFromAnyPublicPage() throws Exception {
        assertThat(render("/movies")).doesNotContain("href=\"/login\"");
        assertThat(render("/shows")).doesNotContain("href=\"/login\"");
    }

    @Test
    void requestingMaintenanceUnauthenticatedRedirectsToLoginThenBackOnSuccess() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/maintenance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"))
                .andReturn().getRequest().getSession();

        mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", ADMIN_USERNAME)
                        .param("password", ADMIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/maintenance*"));

        mockMvc.perform(get("/maintenance").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void loggingOutReturnsToThePublicListing() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/maintenance"))
                .andReturn().getRequest().getSession();
        mockMvc.perform(post("/login")
                .session(session)
                .param("username", ADMIN_USERNAME)
                .param("password", ADMIN_PASSWORD)
                .with(csrf()));

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/maintenance").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void maintenancePageRendersBothReindexTriggersInertWithCsrfTokensPresent() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/maintenance"))
                .andReturn().getRequest().getSession();
        mockMvc.perform(post("/login")
                .session(session)
                .param("username", ADMIN_USERNAME)
                .param("password", ADMIN_PASSWORD)
                .with(csrf()));

        String html = mockMvc.perform(get("/maintenance").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("action=\"/maintenance/movies/reindex\"");
        assertThat(html).contains("action=\"/maintenance/shows/reindex\"");
        assertThat(html).containsPattern("<button[^>]*\\bdisabled\\b[^>]*>Re-index movies</button>");
        assertThat(html).containsPattern("<button[^>]*\\bdisabled\\b[^>]*>Re-index shows</button>");
        assertThat(html).containsPattern("name=\"_csrf\"\\s+value=\"[^\"]+\"");
    }

    @Test
    void explainsThatTriggersAreUnconfiguredWhenNoSourcePathsAreSet() throws Exception {
        MockHttpSession session = login();

        String html = mockMvc.perform(get("/maintenance").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("No movie source paths are configured");
        assertThat(html).contains("No show source paths are configured");
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

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
