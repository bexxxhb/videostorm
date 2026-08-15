package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.maintenance.domain.DuplicateScanRunSummary;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * A duplicate scan run shaped for the run-result table: display strings only, so the template stays
 * free of formatting. The execution timestamp is rendered in UTC and the duration in milliseconds;
 * {@code hasGroups} gates the drill-down link, which is offered only when a run found something.
 *
 * <p>Plain JavaBean rather than a record — Pug4j resolves model properties via {@code getXxx()},
 * which record accessors don't satisfy.
 */
@Getter
@RequiredArgsConstructor
public class DuplicateScanRunView {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final long id;
    private final String executedAt;
    private final String duration;
    private final int groupCount;
    private final boolean hasGroups;

    static DuplicateScanRunView of(DuplicateScanRunSummary run) {
        return new DuplicateScanRunView(
                run.id(),
                TIMESTAMP.format(run.executedAt()),
                format(run.duration()),
                run.groupCount(),
                run.groupCount() > 0);
    }

    private static String format(Duration duration) {
        return duration.toMillis() + " ms";
    }
}
