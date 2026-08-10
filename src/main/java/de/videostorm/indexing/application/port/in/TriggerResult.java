package de.videostorm.indexing.application.port.in;

import java.util.List;

/**
 * The outcome of pulling a re-index trigger. One of three things happened: a new run was
 * {@link Outcome#STARTED} in the background; a run was already {@link Outcome#ALREADY_RUNNING} and
 * the trigger was refused rather than queued; or a pre-flight check found one or more source paths
 * {@link Outcome#PATHS_UNREACHABLE} and aborted before anything was persisted or written.
 *
 * <p>{@link #unreachablePaths()} carries the offending path values for that last case — so the
 * triggering operator can be told exactly which mounts to fix — and is empty for the others.
 */
public record TriggerResult(Outcome outcome, List<String> unreachablePaths) {

    public enum Outcome {
        STARTED,
        ALREADY_RUNNING,
        PATHS_UNREACHABLE
    }

    public TriggerResult {
        unreachablePaths = List.copyOf(unreachablePaths);
    }

    public static TriggerResult started() {
        return new TriggerResult(Outcome.STARTED, List.of());
    }

    public static TriggerResult alreadyRunning() {
        return new TriggerResult(Outcome.ALREADY_RUNNING, List.of());
    }

    public static TriggerResult pathsUnreachable(List<String> unreachablePaths) {
        return new TriggerResult(Outcome.PATHS_UNREACHABLE, unreachablePaths);
    }
}
