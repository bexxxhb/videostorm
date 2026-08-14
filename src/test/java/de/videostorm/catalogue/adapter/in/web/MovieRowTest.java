package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.Movie;
import de.videostorm.catalogue.domain.Rating;
import de.videostorm.catalogue.domain.Year;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MovieRowTest {

    @Test
    void evenIndexGetsTheEvenRowClass() {
        assertThat(MovieRow.from(minimalMovie(), 0, 1).getRowClass()).isEqualTo("row-even");
    }

    @Test
    void oddIndexGetsTheOddRowClass() {
        assertThat(MovieRow.from(minimalMovie(), 1, 2).getRowClass()).isEqualTo("row-odd");
    }

    @Test
    void theRunningNumberIsExposedAsTheIndexDisplay() {
        assertThat(MovieRow.from(minimalMovie(), 0, 42).getIndexDisplay()).isEqualTo("42");
    }

    @Test
    void anUnknownYearRendersAsAnEmptyString() {
        assertThat(MovieRow.from(minimalMovie(), 0, 1).getYear()).isEmpty();
    }

    @Test
    void anAbsentRatingRendersAsAnEmptyString() {
        assertThat(MovieRow.from(minimalMovie(), 0, 1).getRatingDisplay()).isEmpty();
    }

    @Test
    void anAbsentRuntimeRendersAsAnEmptyString() {
        assertThat(MovieRow.from(minimalMovie(), 0, 1).getRuntimeDisplay()).isEmpty();
    }

    @Test
    void aPresentRatingIsFormattedWithItsProvider() {
        Movie movie = new Movie(1, "Heat", Year.of(1995),
                Optional.of(new Rating("TMDB", new BigDecimal("7.8"))), GenreList.EMPTY, Optional.of(170),
                Optional.empty(), Optional.empty(), Optional.empty(), false, false);

        MovieRow row = MovieRow.from(movie, 0, 1);

        assertThat(row.getYear()).isEqualTo("1995");
        assertThat(row.getRatingDisplay()).isEqualTo("7.8 (TMDB)");
        assertThat(row.getRuntimeDisplay()).isEqualTo("170");
    }

    @Test
    void anAbsentResolutionRendersAsAnEmptyString() {
        assertThat(MovieRow.from(minimalMovie(), 0, 1).getResolutionDisplay()).isEmpty();
    }

    @Test
    void aPresentResolutionIsRenderedAsIsFromTheDomain() {
        Movie movie = movieWithResolution("1080p");

        assertThat(MovieRow.from(movie, 0, 1).getResolutionDisplay()).isEqualTo("1080p");
    }

    @Test
    void anAbsentImdbIdLeavesBothTheUrlAndLinkTextEmpty() {
        MovieRow row = MovieRow.from(minimalMovie(), 0, 1);

        assertThat(row.getImdbUrl()).isEmpty();
        assertThat(row.getImdbLinkText()).isEmpty();
    }

    @Test
    void aBlankImdbIdIsTreatedAsAbsent() {
        MovieRow row = MovieRow.from(movieWithImdbId("   "), 0, 1);

        assertThat(row.getImdbUrl()).isEmpty();
        assertThat(row.getImdbLinkText()).isEmpty();
    }

    @Test
    void aPresentImdbIdBuildsTheTitleUrlAndTheFixedLinkText() {
        MovieRow row = MovieRow.from(movieWithImdbId("tt0113277"), 0, 1);

        assertThat(row.getImdbUrl()).isEqualTo("https://www.imdb.com/title/tt0113277/");
        assertThat(row.getImdbLinkText()).isEqualTo("info @ IMDB.com");
    }

    private static Movie movieWithResolution(String resolution) {
        return new Movie(1, "Heat", Year.of(1995), Optional.empty(), GenreList.EMPTY, Optional.empty(),
                Optional.of(resolution), Optional.empty(), Optional.empty(), false, false);
    }

    private static Movie movieWithImdbId(String imdbId) {
        return new Movie(1, "Heat", Year.of(1995), Optional.empty(), GenreList.EMPTY, Optional.empty(),
                Optional.empty(), Optional.of(imdbId), Optional.empty(), false, false);
    }

    @Test
    void anAbsentPlotHasNoLinkAndAnEmptyBase64() {
        MovieRow row = MovieRow.from(minimalMovie(), 0, 1);

        assertThat(row.isHasPlot()).isFalse();
        assertThat(row.getPlotBase64()).isEmpty();
    }

    @Test
    void aBlankPlotIsTreatedAsAbsent() {
        MovieRow row = MovieRow.from(movieWithPlot("   "), 0, 1);

        assertThat(row.isHasPlot()).isFalse();
        assertThat(row.getPlotBase64()).isEmpty();
    }

    @Test
    void aPresentPlotIsExposedAsUtf8Base64() {
        String plot = "L'été: a \"tale\" of <heroes> & foes.\nSecond line.";

        MovieRow row = MovieRow.from(movieWithPlot(plot), 0, 1);

        assertThat(row.isHasPlot()).isTrue();
        assertThat(decode(row.getPlotBase64())).isEqualTo(plot);
    }

    private static Movie movieWithPlot(String plot) {
        return new Movie(1, "Heat", Year.of(1995), Optional.empty(), GenreList.EMPTY, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(plot), false, false);
    }

    @Test
    void aMovieWithoutRawNfoHasNoLinkAndAnEmptyUrl() {
        MovieRow row = MovieRow.from(minimalMovie(), 0, 1);

        assertThat(row.isHasRawNfo()).isFalse();
        assertThat(row.getRawNfoUrl()).isEmpty();
    }

    @Test
    void aMovieWithRawNfoLinksToItsOnDemandNfoEndpoint() {
        MovieRow row = MovieRow.from(movieWithRawNfo(), 0, 1);

        assertThat(row.isHasRawNfo()).isTrue();
        assertThat(row.getRawNfoUrl()).isEqualTo("/movies/1/nfo");
    }

    private static Movie movieWithRawNfo() {
        return new Movie(1, "Heat", Year.of(1995), Optional.empty(), GenreList.EMPTY, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), true, false);
    }

    @Test
    void aMovieWithoutCastHasNoLinkAndAnEmptyUrl() {
        MovieRow row = MovieRow.from(minimalMovie(), 0, 1);

        assertThat(row.isHasActors()).isFalse();
        assertThat(row.getActorsUrl()).isEmpty();
    }

    @Test
    void aMovieWithCastLinksToItsOnDemandActorsEndpoint() {
        MovieRow row = MovieRow.from(movieWithCast(), 0, 1);

        assertThat(row.isHasActors()).isTrue();
        assertThat(row.getActorsUrl()).isEqualTo("/movies/1/actors");
    }

    private static Movie movieWithCast() {
        return new Movie(1, "Heat", Year.of(1995), Optional.empty(), GenreList.EMPTY, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), false, true);
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static Movie minimalMovie() {
        return new Movie(1, "Unscraped Folder", Year.UNKNOWN, Optional.empty(), GenreList.EMPTY, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), false, false);
    }
}
