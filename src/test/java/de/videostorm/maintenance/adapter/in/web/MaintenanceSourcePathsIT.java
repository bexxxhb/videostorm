package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The maintenance page with movies configured but shows not: the unconfigured type explains
 * itself, the configured type does not, and — crucially — no configured path value ever reaches
 * the rendered HTML.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "videostorm.sources.movies=/media/movies,/mnt/films")
class MaintenanceSourcePathsIT extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void showsTheNoteOnlyForTheUnconfiguredTypeAndNeverLeaksAPathValue() throws Exception {
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

        assertThat(html).doesNotContain("No movie source paths are configured");
        assertThat(html).contains("No show source paths are configured");
        assertThat(html).doesNotContain("/media/movies");
        assertThat(html).doesNotContain("/mnt/films");
    }
}
