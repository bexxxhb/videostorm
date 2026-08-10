package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The slug is the entry's identity — normalised title and year — in a stable, filename-safe form,
 * so two folders claiming the same film later collide on the same value.
 */
class SlugTest {

    @Test
    void joinsNormalisedTitleAndYearWithHyphens() {
        assertThat(Slug.forMovie("96 Hours - Taken 3", 2014)).isEqualTo("96-hours-taken-3-2014");
    }

    @Test
    void stripsAccentsAndCaseSoRemakesShareShapeButDifferInYear() {
        assertThat(Slug.forMovie("Mädchen", 1990)).isEqualTo("madchen-1990");
        assertThat(Slug.forMovie("The Thing", 1982)).isEqualTo("the-thing-1982");
        assertThat(Slug.forMovie("The Thing", 2011)).isEqualTo("the-thing-2011");
    }

    @Test
    void marksAMissingYearWithZeroRatherThanDroppingIt() {
        assertThat(Slug.forMovie("Untitled", 0)).isEqualTo("untitled-0");
    }
}
