package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.MoviePage;
import de.videostorm.catalogue.application.port.in.ListMoviesQuery;
import de.videostorm.config.PugViewConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast, DB-free coverage of the web adapter: routing and the pagination-link algebra, with
 * {@link ListMoviesQuery} mocked. The fixed sort, seeded columns and zebra rendering are covered
 * against a real database by {@link MovieListingIT}.
 */
@WebMvcTest(MovieListingController.class)
@Import(PugViewConfiguration.class)
class MovieListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListMoviesQuery listMoviesQuery;

    @Test
    void servesTheMovieListingWithoutAuthenticationAtBothRoutes() throws Exception {
        when(listMoviesQuery.list(1)).thenReturn(new MoviePage(List.of(), 1, 1, 0));

        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/movies")).andExpect(status().isOk());
    }

    @Test
    void defaultsToPageOneWhenNoPageParameterIsGiven() throws Exception {
        when(listMoviesQuery.list(1)).thenReturn(new MoviePage(List.of(), 1, 1, 0));

        mockMvc.perform(get("/movies")).andExpect(status().isOk());

        verify(listMoviesQuery).list(1);
    }

    @Test
    void passesTheRequestedPageNumberThroughToTheQuery() throws Exception {
        when(listMoviesQuery.list(3)).thenReturn(new MoviePage(List.of(), 3, 5, 250));

        mockMvc.perform(get("/movies").param("page", "3")).andExpect(status().isOk());

        verify(listMoviesQuery).list(3);
    }

    @Test
    void onTheFirstPageFirstAndPreviousAreDisabledWhileNextAndLastLink() throws Exception {
        when(listMoviesQuery.list(1)).thenReturn(new MoviePage(List.of(), 1, 3, 120));

        String html = render("/movies");

        assertThat(html)
                .contains("<span class=\"pagination__link pagination__link--disabled\">First</span>")
                .contains("<span class=\"pagination__link pagination__link--disabled\">Previous</span>")
                .contains("<a class=\"pagination__link\" href=\"/movies?page=2\">Next</a>")
                .contains("<a class=\"pagination__link\" href=\"/movies?page=3\">Last</a>");
    }

    @Test
    void onTheLastPageNextAndLastAreDisabledWhileFirstAndPreviousLink() throws Exception {
        when(listMoviesQuery.list(3)).thenReturn(new MoviePage(List.of(), 3, 3, 120));

        String html = render("/movies?page=3");

        assertThat(html)
                .contains("<a class=\"pagination__link\" href=\"/movies?page=1\">First</a>")
                .contains("<a class=\"pagination__link\" href=\"/movies?page=2\">Previous</a>")
                .contains("<span class=\"pagination__link pagination__link--disabled\">Next</span>")
                .contains("<span class=\"pagination__link pagination__link--disabled\">Last</span>");
    }

    @Test
    void rendersEveryMovieColumnHeader() throws Exception {
        when(listMoviesQuery.list(1)).thenReturn(new MoviePage(List.of(), 1, 1, 0));

        String html = render("/movies");

        assertThat(html)
                .contains("<th>Title</th>")
                .contains("<th>Year</th>")
                .contains("<th>Rating</th>")
                .contains("<th>Genres</th>")
                .contains("<th>Runtime</th>");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
