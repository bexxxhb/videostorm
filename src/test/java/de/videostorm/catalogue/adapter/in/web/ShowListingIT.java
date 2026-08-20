package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.PostgresIntegrationTestBase;
import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.TitleNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        jdbcTemplate.update("DELETE FROM show_actor");
        jdbcTemplate.update("DELETE FROM episode");
        jdbcTemplate.update("DELETE FROM show");
    }

    @Test
    void servesTheShowListingWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/shows")).andExpect(status().isOk());
    }

    @Test
    void aPlainRequestRendersTheFullPageAroundTheResults() throws Exception {
        String html = render("/shows");

        assertThat(html).contains("<html").contains("class=\"search\"").contains("id=\"results\"");
    }

    @Test
    void anAjaxRequestRendersOnlyTheResultsFragment() throws Exception {
        insertShow("Breaking Bad", 2008, "ENDED", null, null, List.of());

        String html = renderFragment("/shows");

        assertThat(html).doesNotContain("<html").doesNotContain("class=\"search\"");
        assertThat(html).contains("<td>Breaking Bad</td>").contains("class=\"pagination\"");
    }

    @Test
    void anAjaxRequestHonoursTheSearchQueryJustLikeAFullPageRequest() throws Exception {
        insertShow("Breaking Bad", 2008, "ENDED", null, null, List.of());
        insertShow("Unrelated", 2008, "ENDED", null, null, List.of());

        String html = renderFragment("/shows?q=breaking");

        assertThat(html).contains("<td>Breaking Bad</td>").doesNotContain("<td>Unrelated</td>");
    }

    @Test
    void rendersEveryShowColumnHeaderWithSortableOnesAsLinksAndNoResolution() throws Exception {
        String html = render("/shows");

        assertThat(html)
                .contains("<th>#</th>")
                .contains(">Title " + SortHeader.ASC_MARKER + "</a>")
                .contains(">Started " + SortHeader.NEUTRAL_MARKER + "</a>")
                .contains(">Rating " + SortHeader.NEUTRAL_MARKER + "</a>")
                .contains("<th>Status</th>")
                .contains("<th>Genres</th>")
                .contains("<th>Seasons</th>")
                .contains("<th>Total Episodes</th>")
                .contains("<th>Plot</th>")
                .contains("<th>Actors</th>")
                .contains("<th>IMDb</th>")
                .contains("<th>nfo raw</th>")
                .doesNotContain("Resolution");
    }

    @Test
    void aShowWithAPlotRendersAPlotLinkCarryingTheUtf8Base64Plot() throws Exception {
        String plot = "L'été: a \"tale\" of <heroes> & foes.\nSecond line.";
        insertShowWithPlot("Breaking Bad", plot);

        String html = render("/shows");

        String base64 = Base64.getEncoder().encodeToString(plot.getBytes(StandardCharsets.UTF_8));
        assertThat(html).contains(
                "<a class=\"plot-link\" href=\"#\" data-plot=\"" + base64 + "\" data-title=\"Breaking Bad\">Plot</a>");
    }

    @Test
    void aShowWithoutAPlotRendersAnEmptyPlotCellWithNoLink() throws Exception {
        insertShow("Unscraped Folder", null, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        assertThat(html).doesNotContain("plot-link");
    }

    @Test
    void aShowWithRawNfoRendersARawDataLinkToItsOnDemandNfoEndpoint() throws Exception {
        long id = insertShowWithRawNfo("Breaking Bad", "<tvshow><title>Breaking Bad</title></tvshow>");

        String html = render("/shows");

        assertThat(html).contains(
                "<a class=\"rawnfo-link\" href=\"/shows/" + id + "/nfo\" data-title=\"Breaking Bad\">Raw data</a>");
    }

    @Test
    void aShowWithoutRawNfoRendersAnEmptyCellWithNoLink() throws Exception {
        insertShow("Unscraped Folder", null, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        assertThat(html).doesNotContain("rawnfo-link");
    }

    @Test
    void theRawNfoEndpointServesTheStoredXmlVerbatim() throws Exception {
        String rawNfo = "<tvshow>\n  <title>Breaking Bad</title>\n  <plot>L'été & \"foes\"</plot>\n</tvshow>";
        long id = insertShowWithRawNfo("Breaking Bad", rawNfo);

        mockMvc.perform(get("/shows/" + id + "/nfo"))
                .andExpect(status().isOk())
                .andExpect(content().string(rawNfo));
    }

    @Test
    void theRawNfoEndpointReturns404ForAnUnknownShow() throws Exception {
        mockMvc.perform(get("/shows/999999/nfo")).andExpect(status().isNotFound());
    }

    @Test
    void theRawNfoEndpointReturns404WhenTheShowHasNoRawNfo() throws Exception {
        insertShow("Unscraped Folder", null, "UNKNOWN", null, null, List.of());
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM show WHERE normalized_title = ?", Long.class,
                TitleNormalizer.normalize("Unscraped Folder"));

        mockMvc.perform(get("/shows/" + id + "/nfo")).andExpect(status().isNotFound());
    }

    @Test
    void aShowWithCastRendersAnActorsLinkButCarriesNoActorDetailInTheListingDom() throws Exception {
        insertShow("Breaking Bad", 2008, "ENDED", null, null, List.of());
        long id = idOf("Breaking Bad");
        insertActor(id, "Bryan Cranston", "Walter White", 0, "http://image.tmdb.org/t/p/w185/walt.jpg");

        String html = render("/shows");

        assertThat(html).contains(
                "<a class=\"actors-link\" href=\"/shows/" + id + "/actors\" data-title=\"Breaking Bad\">Actors</a>");
        assertThat(html)
                .doesNotContain("Bryan Cranston")
                .doesNotContain("Walter White")
                .doesNotContain("image.tmdb.org");
    }

    @Test
    void aShowWithoutCastRendersAnEmptyCellWithNoActorsLink() throws Exception {
        insertShow("Unscraped Folder", null, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        assertThat(html).doesNotContain("actors-link");
    }

    @Test
    void theActorsEndpointServesTheCastAsJsonTopBilledFirstWithHttpsThumbs() throws Exception {
        insertShow("Breaking Bad", 2008, "ENDED", null, null, List.of());
        long id = idOf("Breaking Bad");
        insertActor(id, "Bryan Cranston", "Walter White", 0, "http://image.tmdb.org/t/p/w185/walt.jpg");
        insertActor(id, "Extra", null, 1, null);

        mockMvc.perform(get("/shows/" + id + "/actors"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Bryan Cranston"))
                .andExpect(jsonPath("$[0].role").value("Walter White"))
                .andExpect(jsonPath("$[0].thumbUrl").value("https://image.tmdb.org/t/p/w185/walt.jpg"))
                .andExpect(jsonPath("$[1].name").value("Extra"))
                .andExpect(jsonPath("$[1].role").value(nullValue()))
                .andExpect(jsonPath("$[1].thumbUrl").value(nullValue()));
    }

    @Test
    void theActorsEndpointReturnsAnEmptyArrayWhenTheShowHasNoCast() throws Exception {
        insertShow("Unscraped Folder", null, "UNKNOWN", null, null, List.of());
        long id = idOf("Unscraped Folder");

        mockMvc.perform(get("/shows/" + id + "/actors"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void theActorsEndpointReturns404ForAnUnknownShow() throws Exception {
        mockMvc.perform(get("/shows/999999/actors")).andExpect(status().isNotFound());
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
        insertShow("Breaking Bad", 2008, "ENDED", "TVDB", new BigDecimal("9.5"), 1234, List.of("Crime", "Drama"));

        String html = render("/shows");

        assertThat(html).contains("<td>Breaking Bad</td>");
        assertThat(html).contains("<td>2008</td>");
        assertThat(html).contains("<td>ended</td>");
        assertThat(html).contains("<td>9.5 (1.234 votes)</td>");
        assertThat(html).contains("<td title=\"Crime, Drama\">Crime, Drama</td>");
    }

    @Test
    void rendersAShowRatingWithoutVoteCountAsTheBareValue() throws Exception {
        insertShow("Breaking Bad", 2008, "ENDED", "TVDB", new BigDecimal("9.5"), List.of("Crime", "Drama"));

        String html = render("/shows");

        assertThat(html).contains("<td>9.5</td>");
    }

    @Test
    void showsTheDistinctSeasonCountAndTotalEpisodeCount() throws Exception {
        insertShow("Breaking Bad", 2008, "ENDED", null, null, List.of());
        long showId = idOf("Breaking Bad");
        insertEpisode(showId, 1, 1);
        insertEpisode(showId, 1, 2);
        insertEpisode(showId, 2, 1);

        String html = render("/shows");

        // Title, Started, Status, Rating(empty), Genres(empty), Seasons(2), Total Episodes(3).
        assertThat(html).contains(
                "<td>Breaking Bad</td><td>2008</td><td>ended</td><td></td><td title=\"\"></td><td>2</td><td>3</td>");
    }

    @Test
    void aShowWithNoEpisodesRendersZeroSeasonsAndZeroEpisodes() throws Exception {
        insertShow("Unscraped Folder", null, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        assertThat(html).contains(
                "<td>Unscraped Folder</td><td></td><td>unknown</td><td></td><td title=\"\"></td><td>0</td><td>0</td>");
    }

    @Test
    void aShowWithAnImdbIdRendersALinkToItsImdbPage() throws Exception {
        insertShowWithImdb("Breaking Bad", "tt0903747");

        String html = render("/shows");

        assertThat(html).contains(
                "<a class=\"imdb-link\" href=\"https://www.imdb.com/title/tt0903747/\" target=\"_blank\" rel=\"noopener noreferrer\">info @ IMDB.com</a>");
    }

    @Test
    void aShowWithoutAnImdbIdRendersAnEmptyImdbCellWithNoLink() throws Exception {
        insertShow("Unscraped Folder", null, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        assertThat(html).doesNotContain("imdb-link");
    }

    @Test
    void theMoviesPageLabelsItsYearColumnYearNotStarted() throws Exception {
        String html = render("/movies");

        assertThat(html).contains(">Year " + SortHeader.NEUTRAL_MARKER + "</a>").doesNotContain("Started");
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
    void tiesOnTheSortedColumnBreakDeterministicallyOnId() throws Exception {
        insertShow("The Thing", 2011, "UNKNOWN", null, null, List.of()); // inserted first, so lower id
        insertShow("The Thing", 1982, "UNKNOWN", null, null, List.of());

        String html = render("/shows");

        int first = html.indexOf("<td>2011</td>");
        int second = html.indexOf("<td>1982</td>");

        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void sortsByStartedYearAscendingWithUnknownYearsLast() throws Exception {
        insertShow("Old Show", 1980, "UNKNOWN", null, null, List.of());
        insertShow("New Show", 2020, "UNKNOWN", null, null, List.of());
        insertShow("Undated Show", null, "UNKNOWN", null, null, List.of()); // year sentinel 0

        String html = render("/shows?sort=year&dir=asc");

        assertThat(indexOf(html, "Old Show"))
                .isLessThan(indexOf(html, "New Show"))
                .isLessThan(indexOf(html, "Undated Show"));
        assertThat(indexOf(html, "New Show")).isLessThan(indexOf(html, "Undated Show"));
    }

    @Test
    void sortsByRatingDescendingWithUnratedShowsLast() throws Exception {
        insertShow("Lower Rated", 2000, "UNKNOWN", "TVDB", new BigDecimal("6.1"), List.of());
        insertShow("Higher Rated", 2000, "UNKNOWN", "TVDB", new BigDecimal("9.2"), List.of());
        insertShow("Unrated", 2000, "UNKNOWN", null, null, List.of());

        String html = render("/shows?sort=rating&dir=desc");

        assertThat(indexOf(html, "Higher Rated"))
                .isLessThan(indexOf(html, "Lower Rated"))
                .isLessThan(indexOf(html, "Unrated"));
        assertThat(indexOf(html, "Lower Rated")).isLessThan(indexOf(html, "Unrated"));
    }

    @Test
    void firstAndPreviousAreDisabledOnTheFirstPage() throws Exception {
        seedShows(120);

        String html = render("/shows");

        assertThat(html).contains("Page 1 of 3");
        assertThat(html).contains("120 total");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">First</span>");
        assertThat(html).containsPattern("<span class=\"pagination__link pagination__link--disabled\">Previous</span>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/shows?page=2&amp;sort=title&amp;dir=asc\">Next</a>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/shows?page=3&amp;sort=title&amp;dir=asc\">Last</a>");
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
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/shows?page=1&amp;sort=title&amp;dir=asc\">First</a>");
        assertThat(html).contains("<a class=\"pagination__link\" href=\"/shows?page=2&amp;sort=title&amp;dir=asc\">Previous</a>");
        assertThat(html).contains("<td>Show 101</td>");
        assertThat(html).contains("<td>Show 120</td>");
    }

    @Test
    void thePageNumberIsBookmarkableViaAQueryParameter() throws Exception {
        seedShows(120);

        String html = render("/shows?page=2");

        assertThat(html).contains("Page 2 of 3");
        // The running index continues across pages: page 2's first row (the 51st show) is numbered 51.
        assertThat(html).contains("<td>51</td>");
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
        insertShow("El Cuerpo", "The Body", 2012, "UNKNOWN", null, null, null, List.of());
        insertShow("Unrelated Show", null, 2012, "UNKNOWN", null, null, null, List.of());

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
        assertThat(html).contains("href=\"/shows?page=2&amp;q=batman&amp;sort=title&amp;dir=asc\"");
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

    private static int indexOf(String html, String title) {
        int index = html.indexOf("<td>" + title + "</td>");
        assertThat(index).as("row for %s", title).isGreaterThanOrEqualTo(0);
        return index;
    }

    private void seedShows(int count) {
        for (int i = 1; i <= count; i++) {
            insertShow(String.format("Show %03d", i), 2000, "UNKNOWN", null, null, List.of());
        }
    }

    private void insertShow(
            String title, Integer year, String status, String ratingSource, BigDecimal ratingValue,
            List<String> genreValues) {
        insertShow(title, null, year, status, ratingSource, ratingValue, null, genreValues);
    }

    private void insertShow(
            String title, Integer year, String status, String ratingSource, BigDecimal ratingValue,
            Integer ratingVotes, List<String> genreValues) {
        insertShow(title, null, year, status, ratingSource, ratingValue, ratingVotes, genreValues);
    }

    private long idOf(String title) {
        return jdbcTemplate.queryForObject("SELECT id FROM show WHERE title = ?", Long.class, title);
    }

    private void insertEpisode(long showId, int seasonNumber, int episodeNumber) {
        jdbcTemplate.update(
                "INSERT INTO episode (show_id, season_number, episode_number) VALUES (?, ?, ?)",
                showId, seasonNumber, episodeNumber);
    }

    private void insertActor(long showId, String name, String role, Integer billingOrder, String thumb) {
        jdbcTemplate.update(
                "INSERT INTO show_actor (show_id, name, role, billing_order, thumb) VALUES (?, ?, ?, ?, ?)",
                showId, name, role, billingOrder, thumb);
    }

    private void insertShowWithImdb(String title, String imdbId) {
        String normalizedTitle = TitleNormalizer.normalize(title);
        String slug = normalizedTitle.replace(' ', '-') + "-" + System.nanoTime();

        jdbcTemplate.update("""
                INSERT INTO show (title, year, normalized_title, status, genres, imdb_id, slug)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                title, 0, normalizedTitle, "UNKNOWN", new GenreList(List.of()).toStorage(), imdbId, slug);
    }

    private void insertShowWithPlot(String title, String plot) {
        String normalizedTitle = TitleNormalizer.normalize(title);
        String slug = normalizedTitle.replace(' ', '-') + "-" + System.nanoTime();

        jdbcTemplate.update("""
                INSERT INTO show (title, year, normalized_title, status, genres, plot, slug)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                title, 0, normalizedTitle, "UNKNOWN", new GenreList(List.of()).toStorage(), plot, slug);
    }

    private long insertShowWithRawNfo(String title, String rawNfo) {
        String normalizedTitle = TitleNormalizer.normalize(title);
        String slug = normalizedTitle.replace(' ', '-') + "-" + System.nanoTime();

        jdbcTemplate.update("""
                INSERT INTO show (title, year, normalized_title, status, genres, raw_nfo, slug)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                title, 0, normalizedTitle, "UNKNOWN", new GenreList(List.of()).toStorage(), rawNfo, slug);
        return jdbcTemplate.queryForObject("SELECT id FROM show WHERE slug = ?", Long.class, slug);
    }

    private void insertShow(
            String title, String originalTitle, Integer year, String status, String ratingSource,
            BigDecimal ratingValue, Integer ratingVotes, List<String> genreValues) {
        String normalizedTitle = TitleNormalizer.normalize(title);
        String normalizedOriginalTitle = originalTitle == null ? null : TitleNormalizer.normalize(originalTitle);
        String genres = new GenreList(genreValues).toStorage();
        String slug = normalizedTitle.replace(' ', '-') + "-" + (year == null ? 0 : year) + "-" + System.nanoTime();

        jdbcTemplate.update("""
                INSERT INTO show (title, original_title, year, normalized_title, normalized_original_title,
                                   status, rating_source, rating_value, rating_votes, genres, slug)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                title, originalTitle, year == null ? 0 : year, normalizedTitle, normalizedOriginalTitle,
                status, ratingSource, ratingValue, ratingVotes, genres, slug);
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String renderFragment(String path) throws Exception {
        return mockMvc.perform(get(path).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
