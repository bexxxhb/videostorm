package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.domain.ScanReport;
import de.videostorm.sources.domain.SourceType;

/**
 * The scan of a single {@link SourceType}, rebuilding that type's staging from disk. One
 * implementation per type ({@link FilesystemMovieScan}, {@link FilesystemShowScan}); the
 * {@link RoutingLibraryScan} picks the right one for a run so each stays a small, type-focused adapter
 * and a new type is added by dropping in another {@code SourceScan} rather than editing a switch.
 */
interface SourceScan {

    SourceType type();

    ScanReport scan();
}
