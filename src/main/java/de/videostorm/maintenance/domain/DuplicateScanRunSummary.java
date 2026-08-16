package de.videostorm.maintenance.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * A duplicate scan run as the history table reads it: the metadata only, without the groups. The
 * groups themselves are fetched on demand for a single run when the operator opens its drill-down, so
 * listing the history never loads every group and member of every past scan.
 */
public record DuplicateScanRunSummary(long id, Instant executedAt, Duration duration, int groupCount) {
}
