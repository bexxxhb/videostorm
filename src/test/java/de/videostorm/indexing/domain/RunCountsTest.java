package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunCountsTest {

    @Test
    void noneIsTwoZeroes() {
        assertThat(RunCounts.none()).isEqualTo(new RunCounts(0, 0));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new RunCounts(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunCounts(0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
