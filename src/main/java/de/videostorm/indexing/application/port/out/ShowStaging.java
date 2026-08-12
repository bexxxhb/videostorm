package de.videostorm.indexing.application.port.out;

import de.videostorm.indexing.domain.StagedEpisode;
import de.videostorm.indexing.domain.StagedShow;

import java.util.List;

/**
 * Writes parsed shows into the staging tables, which mirror the live catalogue but are never read by
 * the listing. The live catalogue is left untouched here; promoting staging into live is a separate
 * step ({@link CataloguePromotion}). The show counterpart of {@link MovieStaging}.
 */
public interface ShowStaging {

    /** Empties staging so a run always rebuilds from nothing. Called once at the start of a run. */
    void clear();

    /**
     * Writes one show and its ratings, committing on its own so a run's progress survives an
     * interruption entry by entry. Returns the id the staged show was given.
     */
    long stage(StagedShow show);

    /**
     * Writes the episodes discovered under a staged show, against the id {@link #stage} returned. The
     * episodes are already de-duplicated by the caller, so a collision here would be a bug caught by
     * the schema backstop rather than an expected condition. A show with no parseable episodes is
     * staged with none, so this is a no-op for an empty list.
     */
    void stageEpisodes(long showId, List<StagedEpisode> episodes);
}
