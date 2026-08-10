package de.videostorm.indexing.application.port.out;

import de.videostorm.sources.domain.SourcePath;
import de.videostorm.sources.domain.SourceType;

import java.util.List;

/**
 * Checks, before a run is allowed to start, that every configured source path for a
 * {@link SourceType} is actually reachable: it exists, is a directory, is readable, and holds at
 * least one entry. An unmounted drive stats as none of these, so a missing mount is caught here
 * rather than being walked as an empty tree that would wipe the catalogue.
 *
 * <p>Returns the configured paths that fail any of those checks, in configuration order; an empty
 * list means every path passed and the run may proceed. This is the only reachability probe of a
 * run and touches nothing on disk beyond the source roots themselves.
 */
public interface MountPreflight {

    List<SourcePath> unreachable(SourceType type);
}
