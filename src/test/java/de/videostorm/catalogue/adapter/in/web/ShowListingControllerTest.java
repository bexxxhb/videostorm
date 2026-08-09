package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.catalogue.application.ShowPage;
import de.videostorm.catalogue.application.port.in.ListShowsQuery;
import de.videostorm.config.PugViewConfiguration;
import de.videostorm.config.security.AdminUserDetailsService;
import de.videostorm.config.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast, DB-free coverage of the web adapter: routing, the query-parameter pass-through and the
 * pagination-link algebra, with {@link ListShowsQuery} mocked. The fixed sort, seeded columns,
 * zebra rendering and actual search matching are covered against a real database by
 * {@link ShowListingIT}. {@link SecurityConfig} is imported so this slice proves the route
 * stays public under the real filter chain, not just in the absence of one.
 */
@WebMvcTest(ShowListingController.class)
@Import({PugViewConfiguration.class, SecurityConfig.class, AdminUserDetailsService.class})
@TestPropertySource(properties = {
        "videostorm.admin.username=" + PostgresIntegrationTestBase.ADMIN_USERNAME,
        "videostorm.admin.password=" + PostgresIntegrationTestBase.ADMIN_PASSWORD
})
class ShowListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListShowsQuery listShowsQuery;

    @Test
    void servesTheShowListingWithoutAuthentication() throws Exception {
        when(listShowsQuery.list(1, "")).thenReturn(new ShowPage(List.of(), 1, 1, 0, ""));

        mockMvc.perform(get("/shows")).andExpect(status().isOk());
    }

    @Test
    void defaultsToPageOneAndNoQueryWhenNeitherParameterIsGiven() throws Exception {
        when(listShowsQuery.list(1, "")).thenReturn(new ShowPage(List.of(), 1, 1, 0, ""));

        mockMvc.perform(get("/shows")).andExpect(status().isOk());

        verify(listShowsQuery).list(1, "");
    }

    @Test
    void passesTheRequestedPageNumberThroughToTheQuery() throws Exception {
        when(listShowsQuery.list(3, "")).thenReturn(new ShowPage(List.of(), 3, 5, 250, ""));

        mockMvc.perform(get("/shows").param("page", "3")).andExpect(status().isOk());

        verify(listShowsQuery).list(3, "");
    }

    @Test
    void passesTheQueryParameterThroughToTheQuery() throws Exception {
        when(listShowsQuery.list(1, "breaking")).thenReturn(new ShowPage(List.of(), 1, 1, 1, "breaking"));

        mockMvc.perform(get("/shows").param("q", "breaking")).andExpect(status().isOk());

        verify(listShowsQuery).list(1, "breaking");
    }

    @Test
    void echoesTheActiveQueryBackIntoTheSearchInputsValue() throws Exception {
        when(listShowsQuery.list(1, "breaking")).thenReturn(new ShowPage(List.of(), 1, 1, 1, "breaking"));

        String html = render("/shows?q=breaking");

        assertThat(html).contains("value=\"breaking\"");
    }

    @Test
    void onTheFirstPageFirstAndPreviousAreDisabledWhileNextAndLastLink() throws Exception {
        when(listShowsQuery.list(1, "")).thenReturn(new ShowPage(List.of(), 1, 3, 120, ""));

        String html = render("/shows");

        assertThat(html)
                .contains("<span class=\"pagination__link pagination__link--disabled\">First</span>")
                .contains("<span class=\"pagination__link pagination__link--disabled\">Previous</span>")
                .contains("<a class=\"pagination__link\" href=\"/shows?page=2\">Next</a>")
                .contains("<a class=\"pagination__link\" href=\"/shows?page=3\">Last</a>");
    }

    @Test
    void onTheLastPageNextAndLastAreDisabledWhileFirstAndPreviousLink() throws Exception {
        when(listShowsQuery.list(3, "")).thenReturn(new ShowPage(List.of(), 3, 3, 120, ""));

        String html = render("/shows?page=3");

        assertThat(html)
                .contains("<a class=\"pagination__link\" href=\"/shows?page=1\">First</a>")
                .contains("<a class=\"pagination__link\" href=\"/shows?page=2\">Previous</a>")
                .contains("<span class=\"pagination__link pagination__link--disabled\">Next</span>")
                .contains("<span class=\"pagination__link pagination__link--disabled\">Last</span>");
    }

    @Test
    void pagingLinksCarryTheActiveSearchQuery() throws Exception {
        when(listShowsQuery.list(1, "die hard")).thenReturn(new ShowPage(List.of(), 1, 3, 120, "die hard"));

        String html = mockMvc.perform(get("/shows").param("q", "die hard"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("href=\"/shows?page=2&amp;q=die+hard\"");
    }

    @Test
    void rendersEveryShowColumnHeader() throws Exception {
        when(listShowsQuery.list(1, "")).thenReturn(new ShowPage(List.of(), 1, 1, 0, ""));

        String html = render("/shows");

        assertThat(html)
                .contains("<th>Title</th>")
                .contains("<th>Year</th>")
                .contains("<th>Status</th>")
                .contains("<th>Rating</th>")
                .contains("<th>Genres</th>");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
