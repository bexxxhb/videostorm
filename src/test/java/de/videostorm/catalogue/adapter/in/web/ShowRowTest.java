package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.Rating;
import de.videostorm.catalogue.domain.Show;
import de.videostorm.catalogue.domain.ShowStatus;
import de.videostorm.catalogue.domain.Year;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
                Optional.of(new Rating("TVDB", new BigDecimal("9.5"))), GenreList.EMPTY);

        ShowRow row = ShowRow.from(show, 0);

        assertThat(row.getYear()).isEqualTo("2008");
        assertThat(row.getStatusDisplay()).isEqualTo("ended");
        assertThat(row.getRatingDisplay()).isEqualTo("9.5 (TVDB)");
    }

    private static Show minimalShow() {
        return new Show(1, "Unscraped Folder", Year.UNKNOWN, ShowStatus.UNKNOWN, Optional.empty(), GenreList.EMPTY);
    }
}
