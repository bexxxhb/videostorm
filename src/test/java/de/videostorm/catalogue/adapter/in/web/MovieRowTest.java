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
        assertThat(MovieRow.from(minimalMovie(), 0).getRowClass()).isEqualTo("row-even");
    }

    @Test
    void oddIndexGetsTheOddRowClass() {
        assertThat(MovieRow.from(minimalMovie(), 1).getRowClass()).isEqualTo("row-odd");
    }

    @Test
    void anUnknownYearRendersAsAnEmptyString() {
        assertThat(MovieRow.from(minimalMovie(), 0).getYear()).isEmpty();
    }

    @Test
    void anAbsentRatingRendersAsAnEmptyString() {
        assertThat(MovieRow.from(minimalMovie(), 0).getRatingDisplay()).isEmpty();
    }

    @Test
    void anAbsentRuntimeRendersAsAnEmptyString() {
        assertThat(MovieRow.from(minimalMovie(), 0).getRuntimeDisplay()).isEmpty();
    }

    @Test
    void aPresentRatingIsFormattedWithItsProvider() {
        Movie movie = new Movie(1, "Heat", Year.of(1995),
                Optional.of(new Rating("TMDB", new BigDecimal("7.8"))), GenreList.EMPTY, Optional.of(170),
                Optional.empty());

        MovieRow row = MovieRow.from(movie, 0);

        assertThat(row.getYear()).isEqualTo("1995");
        assertThat(row.getRatingDisplay()).isEqualTo("7.8 (TMDB)");
        assertThat(row.getRuntimeDisplay()).isEqualTo("170");
    }

    @Test
    void anAbsentPlotHasNoLinkAndAnEmptyBase64() {
        MovieRow row = MovieRow.from(minimalMovie(), 0);

        assertThat(row.isHasPlot()).isFalse();
        assertThat(row.getPlotBase64()).isEmpty();
    }

    @Test
    void aBlankPlotIsTreatedAsAbsent() {
        MovieRow row = MovieRow.from(movieWithPlot("   "), 0);

        assertThat(row.isHasPlot()).isFalse();
        assertThat(row.getPlotBase64()).isEmpty();
    }

    @Test
    void aPresentPlotIsExposedAsUtf8Base64() {
        String plot = "L'été: a \"tale\" of <heroes> & foes.\nSecond line.";

        MovieRow row = MovieRow.from(movieWithPlot(plot), 0);

        assertThat(row.isHasPlot()).isTrue();
        assertThat(decode(row.getPlotBase64())).isEqualTo(plot);
    }

    private static Movie movieWithPlot(String plot) {
        return new Movie(1, "Heat", Year.of(1995), Optional.empty(), GenreList.EMPTY, Optional.empty(),
                Optional.of(plot));
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static Movie minimalMovie() {
        return new Movie(1, "Unscraped Folder", Year.UNKNOWN, Optional.empty(), GenreList.EMPTY, Optional.empty(),
                Optional.empty());
    }
}
