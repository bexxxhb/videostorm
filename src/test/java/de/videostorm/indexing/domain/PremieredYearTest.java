package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A show's year comes from the leading four digits of its {@code <premiered>} date, and only from
 * there: an absent or unparseable value folds to {@code 0}, the unknown state.
 */
class PremieredYearTest {

    @Test
    void takesTheYearFromAnIsoPremieredDate() {
        assertThat(PremieredYear.from("2008-01-20")).isEqualTo(2008);
    }

    @Test
    void toleratesSurroundingWhitespaceAndABareYear() {
        assertThat(PremieredYear.from("  1999 ")).isEqualTo(1999);
        assertThat(PremieredYear.from("2015")).isEqualTo(2015);
    }

    @Test
    void foldsAnAbsentBlankOrUnparseableValueToZero() {
        assertThat(PremieredYear.from(null)).isZero();
        assertThat(PremieredYear.from("")).isZero();
        assertThat(PremieredYear.from("unknown")).isZero();
        assertThat(PremieredYear.from("12-01-2008")).isZero();
    }
}
