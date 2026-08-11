package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.sources.domain.SourceType;

/**
 * The staging-into-live swap for a single {@link SourceType}. One implementation per type
 * ({@link JdbcMoviePromotion}, {@link JdbcShowPromotion}); the {@link RoutingCataloguePromotion} picks
 * the right one for a run, so each stays a small, type-focused adapter and a new type is added by
 * dropping in another {@code CataloguePromotor} rather than editing a switch.
 */
interface CataloguePromotor {

    SourceType type();

    void promote();
}
