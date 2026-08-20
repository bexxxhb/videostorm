package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.maintenance.domain.DuplicateScanRunSummary;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run view renders the scan metadata as display-ready fields: the timestamp in Europe/Berlin
 * local time, the duration in milliseconds, and a drill-down flag that is set only when the run
 * actually found groups.
 */
class DuplicateScanRunViewTest {

    private static final Instant EXECUTED = Instant.parse("2026-08-14T10:15:30Z");

    @Test
    void rendersTimestampDurationAndCountWithDrillDownOfferedWhenGroupsExist() {
        DuplicateScanRunView view = DuplicateScanRunView.of(
                new DuplicateScanRunSummary(7L, EXECUTED, Duration.ofMillis(1234), 3));

        assertThat(view.getId()).isEqualTo(7L);
        assertThat(view.getExecutedAt()).isEqualTo("2026-08-14 12:15:30");
        assertThat(view.getDuration()).isEqualTo("1234 ms");
        assertThat(view.getGroupCount()).isEqualTo(3);
        assertThat(view.isHasGroups()).isTrue();
    }

    @Test
    void withholdsTheDrillDownFlagWhenTheRunFoundNoGroups() {
        DuplicateScanRunView view = DuplicateScanRunView.of(
                new DuplicateScanRunSummary(8L, EXECUTED, Duration.ZERO, 0));

        assertThat(view.getGroupCount()).isZero();
        assertThat(view.isHasGroups()).isFalse();
    }
}
