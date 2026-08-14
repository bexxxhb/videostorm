package de.videostorm.catalogue.application.port.in;

import java.util.Optional;

/**
 * Inbound port for reading a single movie's raw {@code .nfo} on demand — the listing carries only a
 * flag that it exists, never the text itself.
 */
public interface MovieRawNfoQuery {

    /** The raw {@code .nfo} text for the movie with {@code id}, or empty when there is none. */
    Optional<String> rawNfoFor(long id);
}
