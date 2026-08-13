package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.catalogue.application.ShowPage;
import de.videostorm.catalogue.application.ShowSort;
import de.videostorm.catalogue.application.ShowSortField;
import de.videostorm.catalogue.application.SortDirection;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast, DB-free coverage of the web adapter: routing, the query-parameter pass-through, the
 * pagination-link algebra and the sortable-header markup, with {@link ListShowsQuery} mocked. The
 * actual ordering, seeded columns, zebra rendering and search matching are covered against a real
 * database by {@link ShowListingIT}. {@link SecurityConfig} is imported so this slice proves the
 * route stays public under the real filter chain, not just in the absence of one.
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

    private static ShowPage emptyPage(int pageNumber, int totalPages, long total, String query, ShowSort sort) {
        return new ShowPage(List.of(), pageNumber, totalPages, total, query, sort);
    }

    @Test
    void servesTheShowListingWithoutAuthentication() throws Exception {
        when(listShowsQuery.list(1, "", ShowSort.DEFAULT)).thenReturn(emptyPage(1, 1, 0, "", ShowSort.DEFAULT));

        mockMvc.perform(get("/shows")).andExpect(status().isOk());
    }

    @Test
    void defaultsToPageOneNoQueryAndTitleAscendingWhenNoParametersAreGiven() throws Exception {
        when(listShowsQuery.list(1, "", ShowSort.DEFAULT)).thenReturn(emptyPage(1, 1, 0, "", ShowSort.DEFAULT));

        mockMvc.perform(get("/shows")).andExpect(status().isOk());

        verify(listShowsQuery).list(1, "", ShowSort.DEFAULT);
    }

    @Test
    void passesTheRequestedPageNumberThroughToTheQuery() throws Exception {
        when(listShowsQuery.list(3, "", ShowSort.DEFAULT)).thenReturn(emptyPage(3, 5, 250, "", ShowSort.DEFAULT));

        mockMvc.perform(get("/shows").param("page", "3")).andExpect(status().isOk());

        verify(listShowsQuery).list(3, "", ShowSort.DEFAULT);
    }

    @Test
    void passesTheQueryParameterThroughToTheQuery() throws Exception {
        when(listShowsQuery.list(1, "breaking", ShowSort.DEFAULT))
                .thenReturn(emptyPage(1, 1, 1, "breaking", ShowSort.DEFAULT));

        mockMvc.perform(get("/shows").param("q", "breaking")).andExpect(status().isOk());

        verify(listShowsQuery).list(1, "breaking", ShowSort.DEFAULT);
    }

    @Test
    void mapsTheSortAndDirectionParametersThroughToTheQuery() throws Exception {
        ShowSort sort = new ShowSort(ShowSortField.YEAR, SortDirection.DESC);
        when(listShowsQuery.list(1, "", sort)).thenReturn(emptyPage(1, 1, 0, "", sort));

        mockMvc.perform(get("/shows").param("sort", "year").param("dir", "desc")).andExpect(status().isOk());

        verify(listShowsQuery).list(1, "", sort);
    }

    @Test
    void anUnknownSortParameterFallsBackToTheTitleAscendingDefault() throws Exception {
        when(listShowsQuery.list(eq(1), eq(""), any())).thenReturn(emptyPage(1, 1, 0, "", ShowSort.DEFAULT));

        mockMvc.perform(get("/shows").param("sort", "resolution").param("dir", "nope")).andExpect(status().isOk());

        // "resolution" is not a show column, so it falls back to the default rather than erroring.
        verify(listShowsQuery).list(1, "", ShowSort.DEFAULT);
    }

    @Test
    void echoesTheActiveQueryBackIntoTheSearchInputsValue() throws Exception {
        when(listShowsQuery.list(1, "breaking", ShowSort.DEFAULT))
                .thenReturn(emptyPage(1, 1, 1, "breaking", ShowSort.DEFAULT));

        String html = render("/shows?q=breaking");

        assertThat(html).contains("value=\"breaking\"");
    }

    @Test
    void carriesTheActiveSortIntoTheSearchFormSoANewSearchPreservesIt() throws Exception {
        ShowSort sort = new ShowSort(ShowSortField.RATING, SortDirection.DESC);
        when(listShowsQuery.list(1, "", sort)).thenReturn(emptyPage(1, 1, 0, "", sort));

        String html = render("/shows?sort=rating&dir=desc");

        assertThat(html)
                .contains("name=\"sort\" value=\"rating\"")
                .contains("name=\"dir\" value=\"desc\"");
    }

    @Test
    void onTheFirstPageFirstAndPreviousAreDisabledWhileNextAndLastLink() throws Exception {
        when(listShowsQuery.list(1, "", ShowSort.DEFAULT)).thenReturn(emptyPage(1, 3, 120, "", ShowSort.DEFAULT));

        String html = render("/shows");

        assertThat(html)
                .contains("<span class=\"pagination__link pagination__link--disabled\">First</span>")
                .contains("<span class=\"pagination__link pagination__link--disabled\">Previous</span>")
                .contains("<a class=\"pagination__link\" href=\"/shows?page=2&amp;sort=title&amp;dir=asc\">Next</a>")
                .contains("<a class=\"pagination__link\" href=\"/shows?page=3&amp;sort=title&amp;dir=asc\">Last</a>");
    }

    @Test
    void pagingLinksCarryTheActiveSearchQueryAndSort() throws Exception {
        ShowSort sort = new ShowSort(ShowSortField.RATING, SortDirection.DESC);
        when(listShowsQuery.list(1, "die hard", sort)).thenReturn(emptyPage(1, 3, 120, "die hard", sort));

        String html = mockMvc.perform(get("/shows").param("q", "die hard").param("sort", "rating").param("dir", "desc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("href=\"/shows?page=2&amp;q=die+hard&amp;sort=rating&amp;dir=desc\"");
    }

    @Test
    void rendersSortableHeadersAsLinksAndLeavesTheRestPlainWithNoResolutionColumn() throws Exception {
        when(listShowsQuery.list(1, "", ShowSort.DEFAULT)).thenReturn(emptyPage(1, 1, 0, "", ShowSort.DEFAULT));

        String html = render("/shows");

        assertThat(html).contains(
                "<a class=\"sort-link\" href=\"/shows?sort=title&amp;dir=desc\">Title " + SortHeader.ASC_MARKER + "</a>");
        assertThat(html).contains(
                "<a class=\"sort-link\" href=\"/shows?sort=year&amp;dir=asc\">Started " + SortHeader.NEUTRAL_MARKER + "</a>");
        assertThat(html).contains(
                "<a class=\"sort-link\" href=\"/shows?sort=rating&amp;dir=asc\">Rating " + SortHeader.NEUTRAL_MARKER + "</a>");
        assertThat(html)
                .contains("<th>Status</th>")
                .contains("<th>Seasons</th>")
                .contains("<th>Total Episodes</th>")
                .doesNotContain("Resolution");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
