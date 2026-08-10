package de.videostorm.indexing.application.port.out;

import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.sources.domain.SourceType;

/**
 * Reads the source library for one {@link SourceType} and rebuilds the catalogue, returning what it
 * tallied. This is the only part of a run that touches the disks.
 *
 * <p>In this scope the implementation is a stub that visits nothing and returns
 * {@link RunCounts#none()}; the real filesystem walk arrives in a later ticket.
 */
public interface LibraryScan {

    RunCounts scan(SourceType type);
}
