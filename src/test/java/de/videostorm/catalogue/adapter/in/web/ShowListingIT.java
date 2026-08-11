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
 * listing columns, status rendering, zebra rendering, empty-cell rendering, genre truncation,
 * paging boundaries and counts, and the fixed sort and search — identical rules to
 * {@link MovieListingIT}, applied to the show aggregate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ShowListingIT extends PostgresIntegrationTestBase {

    private static final Pattern ROW_CLASS = Pattern.compile("<tr class=\"(row-\\w+)\">");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCatalogue() {
        // Children first: the show_rating and episode FKs forbid deleting a show while either remains.
        jdbcTemplate.update("DELETE FROM show_rating");
        jdbcTemplate.update("DELETE FROM episode");
        jdbcTemplate.update("DELETE FROM show");
    }

    @Test
    void servesTheShowListingWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/shows")).andExpect(status().isOk());
    }

    @Test
    void rendersEveryShowColumnHeader() throws Exception {
        String html = render("/shows");

        assertThat(html)
                .contains("<th>Title</th>")
                .contains("<th>Year</th>")
                .contains("<th>Status</th>")
                .contains("<th>Rating</th>")
                .contains("<th>Genres</th>");
    }

    @Test
    void rendersAnEmptyTableWhenNothingIsCatalogued() throws Exception {
        String html = render("/shows");

        Matcher body = Pattern.compile("<tbody>(.*?)</tbody>", Pattern.DOTALL).matcher(html);
        assertThat(body.find()).isTrue();
        assertThat(body.group(1)).doesNotContain("<tr");
    }

    @Test
    void rendersASeededShowWithTitleYearStatusRatingAndGenres() throws Exception {
        insertShow("Breaking Bad", 2008, "ENDED", "TVDB", new BigDecimal("9.5"), List.of("Crime", "Drama"));

        String html = render("/shows");

        assertThat(html).contains("<td>Breaking Bad</td>");
        assertThat(html).contains("<td>2008</td>");
        assertThat(html).contains("<td>ended</td>");
        assertThat(html).contains("<td>9.5 (TVDB)</td>");
        assertThat(html).contains("<td title=\"Crime, Drama\">Crime, Drama</td>");
    }

    @Test
    void statusRendersAsEndedContinuingOrUnknown() throws Exception {
        insertShow("Ended Show", 2000, "ENDED", null, null, List.of());
        insertShow("Continuing Show", 2001, "CONTINUING", null, null, List.of());
        insertShow("Unknown Show", 2002, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        assertThat(html).contains("<td>Ended Show</td><td>2000</td><td>ended</td>");
        assertThat(html).contains("<td>Continuing Show</td><td>2001</td><td>continuing</td>");
        assertThat(html).contains("<td>Unknown Show</td><td>2002</td><td>unknown</td>");
    }

    @Test
    void absentRatingAndGenresRenderAsEmptyCellsWithNoPlaceholder() throws Exception {
        insertShow("Unscraped Folder", null, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        assertThat(html).contains(
                "<td>Unscraped Folder</td><td></td><td>unknown</td><td></td><td title=\"\"></td>");
    }

    @Test
    void nineGenresShowTheFirstThreeFollowedByACountMarkerWithFullListOnHover() throws Exception {
        List<String> genres = List.of(
                "Action", "Thriller", "Comedy", "Drama", "Horror",
                "Romance", "Sci-Fi", "Fantasy", "Mystery");
        insertShow("Everything Show", 2022, "CONTINUING", null, null, genres);

        String html = render("/shows");

        assertThat(html).contains(">Action, Thriller, Comedy +6<");
        assertThat(html).contains("title=\"Action, Thriller, Comedy, Drama, Horror, Romance, Sci-Fi, Fantasy, Mystery\"");
    }

    @Test
    void rowsAlternateBetweenTwoShadesOfGrey() throws Exception {
        insertShow("AAA Show", 2000, "UNKNOWN", null, null, List.of());
        insertShow("BBB Show", 2000, "UNKNOWN", null, null, List.of());
        insertShow("CCC Show", 2000, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        Matcher matcher = ROW_CLASS.matcher(html);
        List<String> rowClasses = matcher.results().map(r -> r.group(1)).toList();

        assertThat(rowClasses).containsExactly("row-even", "row-odd", "row-even");
    }

    @Test
    void sortsByNormalizedTitleSoGermanTitlesLandWhereExpected() throws Exception {
        insertShow("Zebra Crossing", 2000, "UNKNOWN", null, null, List.of());
        insertShow("Über", 2000, "UNKNOWN", null, null, List.of()); // Über -> sorts under U
        insertShow("Ärger", 2000, "UNKNOWN", null, null, List.of()); // Ärger -> sorts under A

        String html = render("/shows");

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
        insertShow("The Thing", 2011, "UNKNOWN", null, null, List.of());
        insertShow("The Thing", 1982, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        int earlier = html.indexOf("<td>1982</td>");
        int later = html.indexOf("<td>2011</td>");

        assertThat(earlier).isPositive();
        assertThat(later).isGreaterThan(earlier);
    }

    @Test
    void firstAndPreviousAreDisabledOnTheFirstPage() throws Exception {
        seedShows(120);

        String html = render("/shows");

        assertThat(html).contains("Page 1 of 3");
        assertThat(html).contains("120 total");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">First</span>");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">Previous</span>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/shows?page=2\">Next</a>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/shows?page=3\">Last</a>");
        assertThat(html).contains("<td>Show 001</td>");
        assertThat(html).contains("<td>Show 050</td>");
        assertThat(html).doesNotContain("<td>Show 051</td>");
    }

    @Test
    void nextAndLastAreDisabledOnTheLastPage() throws Exception {
        seedShows(120);

        String html = render("/shows?page=3");

        assertThat(html).contains("Page 3 of 3");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">Next</span>");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">Last</span>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/shows?page=1\">First</a>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/shows?page=2\">Previous</a>");
        assertThat(html).contains("<td>Show 101</td>");
        assertThat(html).contains("<td>Show 120</td>");
    }

    @Test
    void thePageNumberIsBookmarkableViaAQueryParameter() throws Exception {
        seedShows(120);

        String html = render("/shows?page=2");

        assertThat(html).contains("Page 2 of 3");
        assertThat(html).contains("<td>Show 051</td>");
        assertThat(html).contains("<td>Show 100</td>");
        assertThat(html).doesNotContain("<td>Show 001</td>");
        assertThat(html).doesNotContain("<td>Show 101</td>");
    }

    @Test
    void searchMatchesWhenTheNormalizedTitleContainsTheTermIgnoringAccentsAndPunctuation() throws Exception {
        insertShow("96 Hours - Taken 3", 2014, "UNKNOWN", null, null, List.of());
        insertShow("Mädchen", 2000, "UNKNOWN", null, null, List.of());
        insertShow("Unrelated Show", 2014, "UNKNOWN", null, null, List.of());

        assertThat(render("/shows?q=hours+taken")).contains("<td>96 Hours - Taken 3</td>");
        assertThat(render("/shows?q=madchen")).contains("<td>M&auml;dchen</td>");
        assertThat(render("/shows?q=hours+taken")).doesNotContain("<td>Unrelated Show</td>");
    }

    @Test
    void searchMatchesWhenTheNormalizedOriginalTitleContainsTheTerm() throws Exception {
        insertShow("El Cuerpo", "The Body", 2012, "UNKNOWN", null, null, List.of());
        insertShow("Unrelated Show", null, 2012, "UNKNOWN", null, null, List.of());

        String html = render("/shows?q=body");

        assertThat(html).contains("<td>El Cuerpo</td>");
        assertThat(html).doesNotContain("<td>Unrelated Show</td>");
    }

    @Test
    void searchMatchesGenresCaseInsensitivelyBySubstring() throws Exception {
        insertShow("Heat", 1995, "UNKNOWN", null, null, List.of("Crime", "Thriller"));
        insertShow("Other", 1995, "UNKNOWN", null, null, List.of("Comedy"));

        String html = render("/shows?q=THRIL");

        assertThat(html).contains("<td>Heat</td>");
        assertThat(html).doesNotContain("<td>Other</td>");
    }

    @Test
    void aFourDigitTermMatchesTheYearExactly() throws Exception {
        insertShow("The Thing", 1982, "UNKNOWN", null, null, List.of());
        insertShow("The Thing", 2011, "UNKNOWN", null, null, List.of());

        String html = render("/shows?q=1982");

        assertThat(html).contains("<td>1982</td>");
        assertThat(html).doesNotContain("<td>2011</td>");
    }

    @Test
    void anEmptySearchBoxReturnsEveryEntry() throws Exception {
        insertShow("Alpha", 2000, "UNKNOWN", null, null, List.of());
        insertShow("Beta", 2000, "UNKNOWN", null, null, List.of());

        String html = render("/shows?q=");

        assertThat(html).contains("<td>Alpha</td>").contains("<td>Beta</td>");
    }

    @Test
    void theActiveSearchQueryIsEchoedBackIntoTheSearchInput() throws Exception {
        String html = render("/shows?q=batman");

        assertThat(html).contains("value=\"batman\"");
    }

    @Test
    void pagingPreservesTheActiveSearchQuery() throws Exception {
        for (int i = 1; i <= 60; i++) {
            insertShow(String.format("Batman Show %03d", i), 2000, "UNKNOWN", null, null, List.of());
        }
        insertShow("Unrelated", 2000, "UNKNOWN", null, null, List.of());

        String html = render("/shows?q=batman");

        assertThat(html).contains("Page 1 of 2");
        assertThat(html).contains("60 total");
        assertThat(html).contains("href=\"/shows?page=2&amp;q=batman\"");
    }

    @Test
    void bothTabsKeepIndependentPageAndQueryState() throws Exception {
        insertShow("Show Only", 2000, "UNKNOWN", null, null, List.of());
        jdbcTemplate.update("""
                INSERT INTO movie (title, year, normalized_title, slug)
                VALUES ('Movie Only', 2000, 'movie only', 'movie-only-2000')
                """);

        String showsHtml = render("/shows?page=1&q=show");
        String moviesHtml = render("/movies?page=1&q=movie");

        assertThat(showsHtml).contains("<td>Show Only</td>");
        assertThat(moviesHtml).contains("<td>Movie Only</td>");
    }

    private void seedShows(int count) {
        for (int i = 1; i <= count; i++) {
            insertShow(String.format("Show %03d", i), 2000, "UNKNOWN", null, null, List.of());
        }
    }

    private void insertShow(
            String title, Integer year, String status, String ratingSource, BigDecimal ratingValue,
            List<String> genreValues) {
        insertShow(title, null, year, status, ratingSource, ratingValue, genreValues);
    }

    private void insertShow(
            String title, String originalTitle, Integer year, String status, String ratingSource,
            BigDecimal ratingValue, List<String> genreValues) {
        String normalizedTitle = TitleNormalizer.normalize(title);
        String normalizedOriginalTitle = originalTitle == null ? null : TitleNormalizer.normalize(originalTitle);
        String genres = new GenreList(genreValues).toStorage();
        String slug = normalizedTitle.replace(' ', '-') + "-" + (year == null ? 0 : year) + "-" + System.nanoTime();

        jdbcTemplate.update("""
                INSERT INTO show (title, original_title, year, normalized_title, normalized_original_title,
                                   status, rating_source, rating_value, genres, slug)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                title, originalTitle, year == null ? 0 : year, normalizedTitle, normalizedOriginalTitle,
                status, ratingSource, ratingValue, genres, slug);
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
