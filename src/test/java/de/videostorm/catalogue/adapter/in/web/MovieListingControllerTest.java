package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.catalogue.application.MoviePage;
import de.videostorm.catalogue.application.MovieSort;
import de.videostorm.catalogue.application.MovieSortField;
import de.videostorm.catalogue.application.SortDirection;
import de.videostorm.catalogue.application.port.in.ListMoviesQuery;
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
 * pagination-link algebra and the sortable-header markup, with {@link ListMoviesQuery} mocked. The
 * actual ordering, seeded columns, zebra rendering and search matching are covered against a real
 * database by {@link MovieListingIT}. {@link SecurityConfig} is imported so this slice proves the
 * route stays public under the real filter chain, not just in the absence of one.
 */
@WebMvcTest(MovieListingController.class)
@Import({PugViewConfiguration.class, SecurityConfig.class, AdminUserDetailsService.class})
@TestPropertySource(properties = {
        "videostorm.admin.username=" + PostgresIntegrationTestBase.ADMIN_USERNAME,
        "videostorm.admin.password=" + PostgresIntegrationTestBase.ADMIN_PASSWORD
})
class MovieListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListMoviesQuery listMoviesQuery;

    private static MoviePage emptyPage(int pageNumber, int totalPages, long total, String query, MovieSort sort) {
        return new MoviePage(List.of(), pageNumber, totalPages, total, query, sort);
    }

    @Test
    void servesTheMovieListingWithoutAuthenticationAtBothRoutes() throws Exception {
        when(listMoviesQuery.list(1, "", MovieSort.DEFAULT)).thenReturn(emptyPage(1, 1, 0, "", MovieSort.DEFAULT));

        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/movies")).andExpect(status().isOk());
    }

    @Test
    void defaultsToPageOneNoQueryAndTitleAscendingWhenNoParametersAreGiven() throws Exception {
        when(listMoviesQuery.list(1, "", MovieSort.DEFAULT)).thenReturn(emptyPage(1, 1, 0, "", MovieSort.DEFAULT));

        mockMvc.perform(get("/movies")).andExpect(status().isOk());

        verify(listMoviesQuery).list(1, "", MovieSort.DEFAULT);
    }

    @Test
    void passesTheRequestedPageNumberThroughToTheQuery() throws Exception {
        when(listMoviesQuery.list(3, "", MovieSort.DEFAULT)).thenReturn(emptyPage(3, 5, 250, "", MovieSort.DEFAULT));

        mockMvc.perform(get("/movies").param("page", "3")).andExpect(status().isOk());

        verify(listMoviesQuery).list(3, "", MovieSort.DEFAULT);
    }

    @Test
    void passesTheQueryParameterThroughToTheQuery() throws Exception {
        when(listMoviesQuery.list(1, "batman", MovieSort.DEFAULT))
                .thenReturn(emptyPage(1, 1, 1, "batman", MovieSort.DEFAULT));

        mockMvc.perform(get("/movies").param("q", "batman")).andExpect(status().isOk());

        verify(listMoviesQuery).list(1, "batman", MovieSort.DEFAULT);
    }

    @Test
    void mapsTheSortAndDirectionParametersThroughToTheQuery() throws Exception {
        MovieSort sort = new MovieSort(MovieSortField.RATING, SortDirection.DESC);
        when(listMoviesQuery.list(1, "", sort)).thenReturn(emptyPage(1, 1, 0, "", sort));

        mockMvc.perform(get("/movies").param("sort", "rating").param("dir", "desc")).andExpect(status().isOk());

        verify(listMoviesQuery).list(1, "", sort);
    }

    @Test
    void anUnknownSortParameterFallsBackToTheTitleAscendingDefault() throws Exception {
        when(listMoviesQuery.list(eq(1), eq(""), any())).thenReturn(emptyPage(1, 1, 0, "", MovieSort.DEFAULT));

        mockMvc.perform(get("/movies").param("sort", "bogus").param("dir", "sideways")).andExpect(status().isOk());

        verify(listMoviesQuery).list(1, "", MovieSort.DEFAULT);
    }

    @Test
    void echoesTheActiveQueryBackIntoTheSearchInputsValue() throws Exception {
        when(listMoviesQuery.list(1, "batman", MovieSort.DEFAULT))
                .thenReturn(emptyPage(1, 1, 1, "batman", MovieSort.DEFAULT));

        String html = render("/movies?q=batman");

        assertThat(html).contains("value=\"batman\"");
    }

    @Test
    void carriesTheActiveSortIntoTheSearchFormSoANewSearchPreservesIt() throws Exception {
        MovieSort sort = new MovieSort(MovieSortField.YEAR, SortDirection.DESC);
        when(listMoviesQuery.list(1, "", sort)).thenReturn(emptyPage(1, 1, 0, "", sort));

        String html = render("/movies?sort=year&dir=desc");

        assertThat(html)
                .contains("name=\"sort\" value=\"year\"")
                .contains("name=\"dir\" value=\"desc\"");
    }

    @Test
    void onTheFirstPageFirstAndPreviousAreDisabledWhileNextAndLastLink() throws Exception {
        when(listMoviesQuery.list(1, "", MovieSort.DEFAULT)).thenReturn(emptyPage(1, 3, 120, "", MovieSort.DEFAULT));

        String html = render("/movies");

        assertThat(html)
                .contains("<span class=\"pagination__link pagination__link--disabled\">First</span>")
                .contains("<span class=\"pagination__link pagination__link--disabled\">Previous</span>")
                .contains("<a class=\"pagination__link\" href=\"/movies?page=2&amp;sort=title&amp;dir=asc\">Next</a>")
                .contains("<a class=\"pagination__link\" href=\"/movies?page=3&amp;sort=title&amp;dir=asc\">Last</a>");
    }

    @Test
    void pagingLinksCarryTheActiveSearchQueryAndSort() throws Exception {
        MovieSort sort = new MovieSort(MovieSortField.RATING, SortDirection.DESC);
        when(listMoviesQuery.list(1, "die hard", sort)).thenReturn(emptyPage(1, 3, 120, "die hard", sort));

        String html = mockMvc.perform(get("/movies").param("q", "die hard").param("sort", "rating").param("dir", "desc"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).contains("href=\"/movies?page=2&amp;q=die+hard&amp;sort=rating&amp;dir=desc\"");
    }

    @Test
    void rendersSortableHeadersAsTogglingLinksWithMarkersAndLeavesOthersPlain() throws Exception {
        when(listMoviesQuery.list(1, "", MovieSort.DEFAULT)).thenReturn(emptyPage(1, 1, 0, "", MovieSort.DEFAULT));

        String html = render("/movies");

        // Active Title column: link toggles to descending, marker reflects the current ascending sort.
        assertThat(html).contains(
                "<a class=\"sort-link\" href=\"/movies?sort=title&amp;dir=desc\">Title " + SortHeader.ASC_MARKER + "</a>");
        // Inactive sortable columns: link starts ascending, neutral marker.
        assertThat(html).contains(
                "<a class=\"sort-link\" href=\"/movies?sort=year&amp;dir=asc\">Year " + SortHeader.NEUTRAL_MARKER + "</a>");
        assertThat(html).contains(
                "<a class=\"sort-link\" href=\"/movies?sort=resolution&amp;dir=asc\">Resolution "
                        + SortHeader.NEUTRAL_MARKER + "</a>");
        // Non-sortable columns carry no link and no marker.
        assertThat(html).contains("<th>Genres</th>").contains("<th>Runtime</th>").contains("<th>IMDb</th>");
    }

    @Test
    void theActiveColumnShowsItsDirectionAndTogglesTheOtherWay() throws Exception {
        MovieSort sort = new MovieSort(MovieSortField.RATING, SortDirection.DESC);
        when(listMoviesQuery.list(1, "", sort)).thenReturn(emptyPage(1, 1, 0, "", sort));

        String html = render("/movies?sort=rating&dir=desc");

        assertThat(html).contains(
                "<a class=\"sort-link\" href=\"/movies?sort=rating&amp;dir=asc\">Rating " + SortHeader.DESC_MARKER + "</a>");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
