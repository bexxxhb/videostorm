package de.videostorm.catalogue.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RatingTest {

    @Test
    void displaysTheValueTogetherWithTheProviderThatProducedIt() {
        Rating rating = new Rating("TMDB", new BigDecimal("7.8"));

        assertThat(rating.displayLabel()).isEqualTo("7.8 (TMDB)");
    }
}
