package de.videostorm.catalogue.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RatingTest {

    @Test
    void displaysTheValueTogetherWithItsVoteCount() {
        Rating rating = new Rating("TMDB", new BigDecimal("7.8"), 1234);

        assertThat(rating.displayLabel()).isEqualTo("7.8 (1.234 votes)");
    }

    @Test
    void displaysOnlyTheValueWhenNoVoteCountIsPresent() {
        Rating rating = new Rating("TMDB", new BigDecimal("7.8"), null);

        assertThat(rating.displayLabel()).isEqualTo("7.8");
    }
}
