package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.indexing.domain.IndexingRun;
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

    private final long id;
    private final String type;
    private final String status;
    private final String startedAt;
    private final String finishedAt;
    private final int found;
    private final int indexed;
    private final int skipped;
    private final int missingData;
    private final boolean downloadable;

    static IndexingRunView of(IndexingRun run, boolean downloadable) {
        return new IndexingRunView(
                run.id(),
                run.type().plural(),
                run.status().name(),
                TIMESTAMP.format(run.startedAt()),
                run.finishedAt() == null ? "" : TIMESTAMP.format(run.finishedAt()),
                run.counts().found(),
                run.counts().indexed(),
                run.counts().skipped(),
                run.counts().missingData(),
                downloadable);
    }
}
