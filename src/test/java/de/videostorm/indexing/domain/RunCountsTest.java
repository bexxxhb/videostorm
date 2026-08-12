package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunCountsTest {

    @Test
    void noneIsAllZeroes() {
        assertThat(RunCounts.none()).isEqualTo(new RunCounts(0, 0, 0));
    }

    @Test
    void theTwoArgumentFormLeavesSkippedZero() {
        assertThat(new RunCounts(3, 2)).isEqualTo(new RunCounts(3, 2, 0));
        assertThat(new RunCounts(3, 2).skipped()).isZero();
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new RunCounts(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunCounts(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunCounts(0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
