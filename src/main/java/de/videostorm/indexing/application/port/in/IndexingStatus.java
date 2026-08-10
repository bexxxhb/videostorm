package de.videostorm.indexing.application.port.in;

/**
 * What the maintenance page reads: one consistent {@link IndexingOverview snapshot} of the active
 * run and the recent history, both drawn from a single read so the page can never show a run as
 * active and settled at the same time.
 */
public interface IndexingStatus {

    IndexingOverview overview();
}
