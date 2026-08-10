package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.sources.domain.SourceType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * A run shaped for the maintenance page: display strings only, so the template stays free of
 * formatting and never sees a domain object. Timestamps are rendered in UTC; a run still in flight
 * has no finish time.
 *
 * <p>Plain JavaBean rather than a record — Pug4j resolves model properties via {@code getXxx()},
 * which record accessors ({@code xxx()}) don't satisfy.
 */
@Getter
@RequiredArgsConstructor
public class IndexingRunView {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final String type;
    private final String status;
    private final String startedAt;
    private final String finishedAt;
    private final int found;
    private final int indexed;

    static IndexingRunView of(IndexingRun run) {
        return new IndexingRunView(
                displayType(run.type()),
                run.status().name(),
                TIMESTAMP.format(run.startedAt()),
                run.finishedAt() == null ? "" : TIMESTAMP.format(run.finishedAt()),
                run.counts().found(),
                run.counts().indexed());
    }

    private static String displayType(SourceType type) {
        return type == SourceType.MOVIES ? "Movies" : "Shows";
    }
}
