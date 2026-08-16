package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The duplicate-scan maintenance action end to end: the button triggers a persisted scan, the
 * run-result page lists it with a drill-down link, and the link's endpoint serves that run's groups on
 * demand — 404 for an unknown run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DuplicateScanControllerIT extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM duplicate_scan_member");
        jdbcTemplate.update("DELETE FROM duplicate_scan_group");
        jdbcTemplate.update("DELETE FROM duplicate_scan_run");
        jdbcTemplate.update("DELETE FROM movie");
    }

    @Test
    void triggeringAScanPersistsARunShownOnThePageWithAWorkingDrillDown() throws Exception {
        // Two catalogued movies sharing an IMDb id — a true double that now lands in the catalogue since
        // movie de-duplication was removed (issue #47), which is exactly what the scan exists to reveal.
        insertMovie("tt0111161", "The Matrix", "/films/a.mkv");
        insertMovie("tt0111161", "Matrix", "/films/b.mkv");
        MockHttpSession session = login();

        mockMvc.perform(post("/maintenance/duplicates/scan").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maintenance"));

        String html = mockMvc.perform(get("/maintenance").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Scan for duplicate movies");
        assertThat(html).contains("class=\"duplicates-link\"");

        long runId = jdbcTemplate.queryForObject("SELECT id FROM duplicate_scan_run", Long.class);
        mockMvc.perform(get("/maintenance/duplicate-scans/" + runId + "/groups").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].criterion").value("IMDb ID"))
                .andExpect(jsonPath("$[0].sharedValue").value("tt0111161"))
                .andExpect(jsonPath("$[0].members", hasSize(2)));
    }

    @Test
    void drillDownReturnsNotFoundForAnUnknownRun() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/maintenance/duplicate-scans/999999/groups").session(session))
                .andExpect(status().isNotFound());
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

    private void insertMovie(String imdbId, String originalTitle, String sourcePath) {
        // Movie de-duplication is gone, so two rows may share an imdb_id; a unique slug/normalized_title
        // is used only to satisfy the columns, not to keep the rows distinct.
        String unique = String.valueOf(System.nanoTime());
        jdbcTemplate.update("""
                INSERT INTO movie (title, original_title, year, normalized_title, genres, imdb_id, source_path, slug)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "Title", originalTitle, 0, "title-" + unique, "", imdbId, sourcePath, "slug-" + unique);
    }
}
