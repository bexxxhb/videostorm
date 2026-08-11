package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeasonNumberTest {

    @Test
    void seasonZeroIsSpecials() {
        assertThat(new SeasonNumber(0).isSpecials()).isTrue();
    }

    @Test
    void anyOtherSeasonIsNotSpecials() {
        assertThat(new SeasonNumber(1).isSpecials()).isFalse();
        assertThat(new SeasonNumber(12).isSpecials()).isFalse();
    }

    @Test
    void rejectsANegativeSeason() {
        assertThatThrownBy(() -> new SeasonNumber(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
