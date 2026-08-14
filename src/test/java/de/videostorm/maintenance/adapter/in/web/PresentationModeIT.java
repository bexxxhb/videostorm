package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The default view-only mode against the real filter chain and full application context: the
 * catalogue stays public while the whole maintenance area and login are gone — hidden from the
 * chrome and unreachable by direct request, not merely unlinked. Overrides the base class's
 * {@code maintenance} default back to {@code presentation}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "application.operating.mode=presentation")
class PresentationModeIT extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theCatalogueAndItsAssetsStayPublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/movies")).andExpect(status().isOk());
        mockMvc.perform(get("/shows")).andExpect(status().isOk());
        mockMvc.perform(get("/css/videostorm.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/content-dialog.js")).andExpect(status().isOk());
    }

    @Test
    void neitherTheMaintenanceNorTheLoginLinkAppearsOnAnyPublicPage() throws Exception {
        assertThat(render("/movies"))
                .doesNotContain("href=\"/maintenance\"")
                .doesNotContain("href=\"/login\"");
        assertThat(render("/shows"))
                .doesNotContain("href=\"/maintenance\"")
                .doesNotContain("href=\"/login\"");
    }

    @Test
    void theMaintenancePageIsNotReachable() throws Exception {
        mockMvc.perform(get("/maintenance")).andExpect(status().isForbidden());
    }

    @Test
    void neitherReindexTriggerCanStartARunEvenWithAValidCsrfToken() throws Exception {
        mockMvc.perform(post("/maintenance/movies/reindex").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/maintenance/shows/reindex").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theLoginPageIsNotReachableAndNoLoginCanBePerformed() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isForbidden());

        mockMvc.perform(post("/login")
                        .param("username", ADMIN_USERNAME)
                        .param("password", ADMIN_PASSWORD)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
