package de.videostorm.catalogue.application.port.in;

import de.videostorm.catalogue.domain.CastMember;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for reading a single show's cast on demand — the listing carries only a flag that a
 * cast exists, never the performers themselves.
 */
public interface ShowCastQuery {

    /**
     * The cast of the show with {@code id}, top-billed first. Empty Optional when there is no such
     * show; a present (possibly empty) list when there is.
     */
    Optional<List<CastMember>> castFor(long id);
}
