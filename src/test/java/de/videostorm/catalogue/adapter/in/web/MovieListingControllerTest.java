package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.config.PugViewConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovieListingController.class)
@Import(PugViewConfiguration.class)
class MovieListingControllerTest {

    private static final Pattern TABLE_BODY = Pattern.compile("<tbody>(.*?)</tbody>", Pattern.DOTALL);

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/movies"})
    void servesTheMovieListingWithoutAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void rendersAMoviesTab() throws Exception {
        String html = render();

        assertThat(html).contains("<title>Videostorm</title>");
        assertThat(html).containsPattern("<a[^>]*href=\"/movies\"[^>]*>\\s*Movies\\s*</a>");
    }

    @Test
    void rendersEveryMovieColumnHeader() throws Exception {
        String html = render();

        assertThat(html)
                .contains("<th>Title</th>")
                .contains("<th>Year</th>")
                .contains("<th>Rating</th>")
                .contains("<th>Genres</th>")
                .contains("<th>Runtime</th>");
    }

    @Test
    void rendersAnEmptyTableWhenNothingIsCatalogued() throws Exception {
        String html = render();

        Matcher body = TABLE_BODY.matcher(html);
        assertThat(body.find()).as("movie table has a tbody").isTrue();
        assertThat(body.group(1)).as("tbody holds no rows").doesNotContain("<tr");
    }

    private String render() throws Exception {
        return mockMvc.perform(get("/movies"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
