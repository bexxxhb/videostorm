package de.videostorm.catalogue.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YearTest {

    @Test
    void zeroIsUnknown() {
        assertThat(Year.of(0)).isEqualTo(Year.UNKNOWN);
        assertThat(Year.UNKNOWN.isKnown()).isFalse();
    }

    @Test
    void aNegativeValueIsAlsoTreatedAsUnknown() {
        assertThat(Year.of(-1)).isEqualTo(Year.UNKNOWN);
    }

    @Test
    void aPositiveValueIsKnown() {
        Year year = Year.of(1984);

        assertThat(year.isKnown()).isTrue();
        assertThat(year.value()).isEqualTo(1984);
    }
}
