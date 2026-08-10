package de.videostorm.indexing.application.port.out;

import de.videostorm.sources.domain.SourceType;

/**
 * Promotes the staged catalogue for one {@link SourceType} into the live catalogue, replacing it in
 * a single step. Runs after a successful scan has finished writing staging (issue #11).
 *
 * <p>The promotion is one transaction: the live rows for the type are deleted and the staged rows
 * copied across verbatim — ids and all — so a reader browsing the listing throughout a run sees the
 * complete previous catalogue right up to the commit, then the complete new one, never an empty or
 * half-built list. The swap is scoped to the type, so re-indexing movies leaves shows untouched, and
 * a promotion that throws leaves the live catalogue exactly as it was.
 *
 * <p>A type with no importer yet is a no-op, mirroring the scan.
 */
public interface CataloguePromotion {

    void promote(SourceType type);
}
