package de.videostorm.catalogue.application.port.in;

import de.videostorm.catalogue.domain.CastMember;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for reading a single movie's cast on demand — the listing carries only a flag that a
 * cast exists, never the performers themselves.
 */
public interface MovieCastQuery {

    /**
     * The cast of the movie with {@code id}, top-billed first. Empty Optional when there is no such
     * movie; a present (possibly empty) list when there is.
     */
    Optional<List<CastMember>> castFor(long id);
}
