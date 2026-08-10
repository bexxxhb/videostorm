package de.videostorm.indexing.application.port.out;

import de.videostorm.indexing.domain.StagedMovie;

/**
 * Writes parsed movies into the staging tables, which mirror the live catalogue but are never read
 * by the listing. The live catalogue is left untouched here; promoting staging into live is a
 * separate step (issue #11).
 */
public interface MovieStaging {

    /** Empties staging so a run always rebuilds from nothing. Called once at the start of a run. */
    void clear();

    /**
     * Writes one movie and its ratings, committing on its own so a run's progress survives an
     * interruption file by file. Returns the id the staged movie was given.
     */
    long stage(StagedMovie movie);
}
