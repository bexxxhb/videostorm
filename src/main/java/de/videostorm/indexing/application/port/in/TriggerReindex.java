package de.videostorm.indexing.application.port.in;

import de.videostorm.sources.domain.SourceType;

/**
 * Starts a background re-index for one {@link SourceType} and returns immediately. Only one run may
 * be active across both types; a trigger pulled while a run is in progress is refused, never
 * queued.
 *
 * <p>Before a run is started, every configured source path for the type is checked for
 * reachability. If any is unreachable the trigger aborts without persisting a run or touching the
 * catalogue, and the {@link TriggerResult} names the offending paths so the operator can fix the
 * mount rather than have an unmounted drive silently empty the catalogue.
 */
public interface TriggerReindex {

    TriggerResult trigger(SourceType type);
}
