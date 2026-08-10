package de.videostorm.indexing.application.port.in;

import de.videostorm.sources.domain.SourceType;

/**
 * Starts a background re-index for one {@link SourceType} and returns immediately. Only one run may
 * be active across both types; a trigger pulled while a run is in progress is refused, never
 * queued.
 */
public interface TriggerReindex {

    TriggerResult trigger(SourceType type);
}
