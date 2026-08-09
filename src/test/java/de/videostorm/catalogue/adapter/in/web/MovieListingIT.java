package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.TitleNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seam B: HTTP against a database seeded directly, with no indexing involved. Covers the
 * listing columns, zebra rendering, empty-cell rendering, genre truncation, paging boundaries
 * and counts, and the fixed sort including German titles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MovieListingIT extends PostgresIntegrationTestBase {

    private static final Pattern ROW_CLASS = Pattern.compile("<tr class=\"(row-\\w+)\">");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCatalogue() {
        jdbcTemplate.update("DELETE FROM movie");
    }

    @Test
    void servesTheMovieListingWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/movies")).andExpect(status().isOk());
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void rendersEveryMovieColumnHeader() throws Exception {
        String html = render("/movies");

        assertThat(html)
                .contains("<th>Title</th>")
                .contains("<th>Year</th>")
                .contains("<th>Rating</th>")
                .contains("<th>Genres</th>")
                .contains("<th>Runtime</th>");
    }

    @Test
    void rendersAnEmptyTableWhenNothingIsCatalogued() throws Exception {
        String html = render("/movies");

        Matcher body = Pattern.compile("<tbody>(.*?)</tbody>", Pattern.DOTALL).matcher(html);
        assertThat(body.find()).isTrue();
        assertThat(body.group(1)).doesNotContain("<tr");
    }

    @Test
    void rendersASeededMovieWithTitleYearRatingGenresAndRuntime() throws Exception {
        insertMovie("Heat", 1995, "TMDB", new BigDecimal("7.8"), List.of("Crime", "Thriller"), 170);

        String html = render("/movies");

        assertThat(html).contains("<td>Heat</td>");
        assertThat(html).contains("<td>1995</td>");
        assertThat(html).contains("<td>7.8 (TMDB)</td>");
        assertThat(html).contains("<td title=\"Crime, Thriller\">Crime, Thriller</td>");
        assertThat(html).contains("<td>170</td>");
    }

    @Test
    void absentRatingGenresAndRuntimeRenderAsEmptyCellsWithNoPlaceholder() throws Exception {
        insertMovie("Unscraped Folder", null, null, null, List.of(), null);

        String html = render("/movies");

        assertThat(html).contains(
                "<td>Unscraped Folder</td><td></td><td></td><td title=\"\"></td><td></td>");
    }

    @Test
    void nineGenresShowTheFirstThreeFollowedByACountMarkerWithFullListOnHover() throws Exception {
        List<String> genres = List.of(
                "Action", "Thriller", "Comedy", "Drama", "Horror",
                "Romance", "Sci-Fi", "Fantasy", "Mystery");
        insertMovie("Everything Everywhere", 2022, null, null, genres, null);

        String html = render("/movies");

        assertThat(html).contains(">Action, Thriller, Comedy +6<");
        assertThat(html).contains("title=\"Action, Thriller, Comedy, Drama, Horror, Romance, Sci-Fi, Fantasy, Mystery\"");
    }

    @Test
    void rowsAlternateBetweenTwoShadesOfGrey() throws Exception {
        insertMovie("AAA Movie", 2000, null, null, List.of(), null);
        insertMovie("BBB Movie", 2000, null, null, List.of(), null);
        insertMovie("CCC Movie", 2000, null, null, List.of(), null);

        String html = render("/movies");

        Matcher matcher = ROW_CLASS.matcher(html);
        List<String> rowClasses = matcher.results().map(r -> r.group(1)).toList();

        assertThat(rowClasses).containsExactly("row-even", "row-odd", "row-even");
    }

    @Test
    void sortsByNormalizedTitleSoGermanTitlesLandWhereExpected() throws Exception {
        insertMovie("Zebra Crossing", 2000, null, null, List.of(), null);
        insertMovie("Über", 2000, null, null, List.of(), null); // Über -> sorts under U
        insertMovie("Ärger", 2000, null, null, List.of(), null); // Ärger -> sorts under A

        String html = render("/movies");

        // Pug4j HTML-escapes interpolated text, rendering non-ASCII letters as named entities.
        int argerIndex = html.indexOf("<td>&Auml;rger</td>");
        int uberIndex = html.indexOf("<td>&Uuml;ber</td>");
        int zebraIndex = html.indexOf("<td>Zebra Crossing</td>");

        assertThat(argerIndex).isPositive();
        assertThat(uberIndex).isGreaterThan(argerIndex);
        assertThat(zebraIndex).isGreaterThan(uberIndex);
    }

    @Test
    void tiesBreakDeterministicallyOnYearThenId() throws Exception {
        insertMovie("The Thing", 2011, null, null, List.of(), null);
        insertMovie("The Thing", 1982, null, null, List.of(), null);

        String html = render("/movies");

        int earlier = html.indexOf("<td>1982</td>");
        int later = html.indexOf("<td>2011</td>");

        assertThat(earlier).isPositive();
        assertThat(later).isGreaterThan(earlier);
    }

    @Test
    void firstAndPreviousAreDisabledOnTheFirstPage() throws Exception {
        seedMovies(120);

        String html = render("/movies");

        assertThat(html).contains("Page 1 of 3");
        assertThat(html).contains("120 total");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">First</span>");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">Previous</span>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/movies?page=2\">Next</a>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/movies?page=3\">Last</a>");
        assertThat(html).contains("<td>Movie 001</td>");
        assertThat(html).contains("<td>Movie 050</td>");
        assertThat(html).doesNotContain("<td>Movie 051</td>");
    }

    @Test
    void nextAndLastAreDisabledOnTheLastPage() throws Exception {
        seedMovies(120);

        String html = render("/movies?page=3");

        assertThat(html).contains("Page 3 of 3");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">Next</span>");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">Last</span>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/movies?page=1\">First</a>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/movies?page=2\">Previous</a>");
        assertThat(html).contains("<td>Movie 101</td>");
        assertThat(html).contains("<td>Movie 120</td>");
    }

    @Test
    void thePageNumberIsBookmarkableViaAQueryParameter() throws Exception {
        seedMovies(120);

        String html = render("/movies?page=2");

        assertThat(html).contains("Page 2 of 3");
        assertThat(html).contains("<td>Movie 051</td>");
        assertThat(html).contains("<td>Movie 100</td>");
        assertThat(html).doesNotContain("<td>Movie 001</td>");
        assertThat(html).doesNotContain("<td>Movie 101</td>");
    }

    @Test
    void searchMatchesWhenTheNormalizedTitleContainsTheTermIgnoringAccentsAndPunctuation() throws Exception {
        insertMovie("96 Hours - Taken 3", 2014, null, null, List.of(), null);
        insertMovie("Mädchen", 2000, null, null, List.of(), null);
        insertMovie("Unrelated Movie", 2014, null, null, List.of(), null);

        assertThat(render("/movies?q=hours+taken")).contains("<td>96 Hours - Taken 3</td>");
        assertThat(render("/movies?q=madchen")).contains("<td>M&auml;dchen</td>");
        assertThat(render("/movies?q=hours+taken")).doesNotContain("<td>Unrelated Movie</td>");
    }

    @Test
    void searchMatchesWhenTheNormalizedOriginalTitleContainsTheTerm() throws Exception {
        insertMovie("El Cuerpo", "The Body", 2012, null, null, List.of(), null);
        insertMovie("Unrelated Movie", null, 2012, null, null, List.of(), null);

        String html = render("/movies?q=body");

        assertThat(html).contains("<td>El Cuerpo</td>");
        assertThat(html).doesNotContain("<td>Unrelated Movie</td>");
    }

    @Test
    void searchMatchesGenresCaseInsensitivelyBySubstring() throws Exception {
        insertMovie("Heat", 1995, null, null, List.of("Crime", "Thriller"), null);
        insertMovie("Other", 1995, null, null, List.of("Comedy"), null);

        String html = render("/movies?q=THRIL");

        assertThat(html).contains("<td>Heat</td>");
        assertThat(html).doesNotContain("<td>Other</td>");
    }

    @Test
    void aFourDigitTermMatchesTheYearExactly() throws Exception {
        insertMovie("The Thing", 1982, null, null, List.of(), null);
        insertMovie("The Thing", 2011, null, null, List.of(), null);

        String html = render("/movies?q=1982");

        assertThat(html).contains("<td>1982</td>");
        assertThat(html).doesNotContain("<td>2011</td>");
    }

    @Test
    void numericTermsOfOtherLengthsNeverMatchTheYearButCanStillMatchTheTitle() throws Exception {
        insertMovie("Movie 82", 1982, null, null, List.of(), null);
        insertMovie("Another Film", 1982, null, null, List.of(), null);

        String html = render("/movies?q=82");

        assertThat(html).contains("<td>Movie 82</td>");
        assertThat(html).doesNotContain("<td>Another Film</td>");
    }

    @Test
    void anEmptySearchBoxReturnsEveryEntry() throws Exception {
        insertMovie("Alpha", 2000, null, null, List.of(), null);
        insertMovie("Beta", 2000, null, null, List.of(), null);

        String html = render("/movies?q=");

        assertThat(html).contains("<td>Alpha</td>").contains("<td>Beta</td>");
    }

    @Test
    void theGenreDelimiterIsStrippedFromUserInputSoATermCannotMatchAcrossTwoGenres() throws Exception {
        insertMovie("Spanning Test", 2000, null, null, List.of("AB", "CD"), null); // stored as |AB|CD|

        // Without stripping the delimiter, "B|C" would match the raw stored string "|AB|CD|",
        // spanning the boundary between the AB and CD genres.
        String html = mockMvc.perform(get("/movies").param("q", "B|C"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).doesNotContain("<td>Spanning Test</td>");
    }

    @Test
    void theActiveSearchQueryIsEchoedBackIntoTheSearchInput() throws Exception {
        String html = render("/movies?q=batman");

        assertThat(html).contains("value=\"batman\"");
    }

    @Test
    void pagingPreservesTheActiveSearchQuery() throws Exception {
        for (int i = 1; i <= 60; i++) {
            insertMovie(String.format("Batman Movie %03d", i), 2000, null, null, List.of(), null);
        }
        insertMovie("Unrelated", 2000, null, null, List.of(), null);

        String html = render("/movies?q=batman");

        assertThat(html).contains("Page 1 of 2");
        assertThat(html).contains("60 total");
        assertThat(html).contains("href=\"/movies?page=2&amp;q=batman\"");
    }

    private void seedMovies(int count) {
        for (int i = 1; i <= count; i++) {
            insertMovie(String.format("Movie %03d", i), 2000, null, null, List.of(), null);
        }
    }

    private void insertMovie(
            String title, Integer year, String ratingSource, BigDecimal ratingValue,
            List<String> genreValues, Integer runtimeMinutes) {
        insertMovie(title, null, year, ratingSource, ratingValue, genreValues, runtimeMinutes);
    }

    private void insertMovie(
            String title, String originalTitle, Integer year, String ratingSource, BigDecimal ratingValue,
            List<String> genreValues, Integer runtimeMinutes) {
        String normalizedTitle = TitleNormalizer.normalize(title);
        String normalizedOriginalTitle = originalTitle == null ? null : TitleNormalizer.normalize(originalTitle);
        String genres = new GenreList(genreValues).toStorage();
        String slug = normalizedTitle.replace(' ', '-') + "-" + (year == null ? 0 : year) + "-" + System.nanoTime();

        jdbcTemplate.update("""
                INSERT INTO movie (title, original_title, year, normalized_title, normalized_original_title,
                                    rating_source, rating_value, genres, runtime_minutes, slug)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                title, originalTitle, year == null ? 0 : year, normalizedTitle, normalizedOriginalTitle,
                ratingSource, ratingValue, genres, runtimeMinutes, slug);
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
