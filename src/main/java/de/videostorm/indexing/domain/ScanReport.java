package de.videostorm.indexing.domain;

import java.util.List;

/**
 * What one scan produced: the {@link RunCounts} it tallied and every {@link RunIssue} it found worth
 * recording. The issues are carried out of the disk-touching scan and attached to the run by the
 * service, keeping run identity out of the filesystem adapter.
 */
public record ScanReport(RunCounts counts, List<RunIssue> issues) {

    public ScanReport {
        if (counts == null) {
            throw new IllegalArgumentException("Scan report counts must not be null");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** A scan that visited nothing: no counts, no issues. */
    public static ScanReport none() {
        return new ScanReport(RunCounts.none(), List.of());
    }
}
