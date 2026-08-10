package de.videostorm.indexing.application.port.out;

import de.videostorm.indexing.domain.ScanReport;
import de.videostorm.sources.domain.SourceType;

/**
 * Reads the source library for one {@link SourceType} and rebuilds the staging catalogue, returning a
 * {@link ScanReport}: what it tallied plus everything it found questionable. This is the only part of
 * a run that touches the disks. The report's issues carry no run identity — the service attaches them
 * to the run it owns.
 */
public interface LibraryScan {

    ScanReport scan(SourceType type);
}
