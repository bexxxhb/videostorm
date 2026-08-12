package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.Rating;
import de.videostorm.catalogue.domain.Show;
import de.videostorm.catalogue.domain.ShowStatus;
import de.videostorm.catalogue.domain.Year;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShowRowTest {

    @Test
    void evenIndexGetsTheEvenRowClass() {
        assertThat(ShowRow.from(minimalShow(), 0).getRowClass()).isEqualTo("row-even");
    }

    @Test
    void oddIndexGetsTheOddRowClass() {
        assertThat(ShowRow.from(minimalShow(), 1).getRowClass()).isEqualTo("row-odd");
    }

    @Test
    void anUnknownYearRendersAsAnEmptyString() {
        assertThat(ShowRow.from(minimalShow(), 0).getYear()).isEmpty();
    }

    @Test
    void anAbsentRatingRendersAsAnEmptyString() {
        assertThat(ShowRow.from(minimalShow(), 0).getRatingDisplay()).isEmpty();
    }

    @Test
    void anUnknownStatusRendersAsUnknown() {
        assertThat(ShowRow.from(minimalShow(), 0).getStatusDisplay()).isEqualTo("unknown");
    }

    @Test
    void aPresentRatingAndStatusAreFormatted() {
        Show show = new Show(1, "Breaking Bad", Year.of(2008), ShowStatus.ENDED,
                Optional.of(new Rating("TVDB", new BigDecimal("9.5"))), GenreList.EMPTY, Optional.empty(),
                5, 62, Optional.empty());

        ShowRow row = ShowRow.from(show, 0);

        assertThat(row.getYear()).isEqualTo("2008");
        assertThat(row.getStatusDisplay()).isEqualTo("ended");
        assertThat(row.getRatingDisplay()).isEqualTo("9.5 (TVDB)");
    }

    @Test
    void seasonAndEpisodeCountsAreRenderedAsStrings() {
        Show show = new Show(1, "Breaking Bad", Year.of(2008), ShowStatus.ENDED, Optional.empty(),
                GenreList.EMPTY, Optional.empty(), 5, 62, Optional.empty());

        ShowRow row = ShowRow.from(show, 0);

        assertThat(row.getSeasonsDisplay()).isEqualTo("5");
        assertThat(row.getEpisodesDisplay()).isEqualTo("62");
    }

    @Test
    void aShowWithNoEpisodesRendersZeroSeasonsAndZeroEpisodes() {
        ShowRow row = ShowRow.from(minimalShow(), 0);

        assertThat(row.getSeasonsDisplay()).isEqualTo("0");
        assertThat(row.getEpisodesDisplay()).isEqualTo("0");
    }

    @Test
    void anAbsentImdbIdLeavesBothTheUrlAndLinkTextEmpty() {
        ShowRow row = ShowRow.from(minimalShow(), 0);

        assertThat(row.getImdbUrl()).isEmpty();
        assertThat(row.getImdbLinkText()).isEmpty();
    }

    @Test
    void aBlankImdbIdIsTreatedAsAbsent() {
        ShowRow row = ShowRow.from(showWithImdbId("   "), 0);

        assertThat(row.getImdbUrl()).isEmpty();
        assertThat(row.getImdbLinkText()).isEmpty();
    }

    @Test
    void aPresentImdbIdBuildsTheTitleUrlAndTheFixedLinkText() {
        ShowRow row = ShowRow.from(showWithImdbId("tt0903747"), 0);

        assertThat(row.getImdbUrl()).isEqualTo("https://www.imdb.com/title/tt0903747/");
        assertThat(row.getImdbLinkText()).isEqualTo("info @ IMDB.com");
    }

    private static Show showWithImdbId(String imdbId) {
        return new Show(1, "Breaking Bad", Year.of(2008), ShowStatus.ENDED, Optional.empty(), GenreList.EMPTY,
                Optional.empty(), 5, 62, Optional.of(imdbId));
    }

    @Test
    void anAbsentPlotHasNoLinkAndAnEmptyBase64() {
        ShowRow row = ShowRow.from(minimalShow(), 0);

        assertThat(row.isHasPlot()).isFalse();
        assertThat(row.getPlotBase64()).isEmpty();
    }

    @Test
    void aBlankPlotIsTreatedAsAbsent() {
        ShowRow row = ShowRow.from(showWithPlot("   "), 0);

        assertThat(row.isHasPlot()).isFalse();
        assertThat(row.getPlotBase64()).isEmpty();
    }

    @Test
    void aPresentPlotIsExposedAsUtf8Base64() {
        String plot = "L'été: a \"tale\" of <heroes> & foes.\nSecond line.";

        ShowRow row = ShowRow.from(showWithPlot(plot), 0);

        assertThat(row.isHasPlot()).isTrue();
        assertThat(decode(row.getPlotBase64())).isEqualTo(plot);
    }

    private static Show showWithPlot(String plot) {
        return new Show(1, "Breaking Bad", Year.of(2008), ShowStatus.ENDED, Optional.empty(), GenreList.EMPTY,
                Optional.of(plot), 5, 62, Optional.empty());
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static Show minimalShow() {
        return new Show(1, "Unscraped Folder", Year.UNKNOWN, ShowStatus.UNKNOWN, Optional.empty(), GenreList.EMPTY,
                Optional.empty(), 0, 0, Optional.empty());
    }
}
