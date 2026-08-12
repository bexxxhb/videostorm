package de.videostorm.indexing.application.port.out;

import de.videostorm.indexing.domain.IndexingRun;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for indexing runs. History is append-only from the catalogue's point of view: a
 * re-index writes new run records and settles old ones, but never deletes them.
 */
public interface IndexingRunRepository {

    /** Persists a new run or the settled version of an existing one, returning it with its id. */
    IndexingRun save(IndexingRun run);

    /**
     * The single active run, if one exists. At most one run is ever active across both types; the
     * schema enforces this with a partial unique index, so this can never return more than one.
     */
    Optional<IndexingRun> findActiveRun();

    /** The most recent runs, newest first, capped at {@code limit}. */
    List<IndexingRun> findRecent(int limit);

    /** Every run, newest first. Summaries are retained indefinitely, so the whole history is returned. */
    List<IndexingRun> findAll();
}
