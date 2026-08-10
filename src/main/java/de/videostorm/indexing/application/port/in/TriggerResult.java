package de.videostorm.indexing.application.port.in;

/**
 * The outcome of pulling a re-index trigger: either a new run was started in the background, or one
 * was already active and the trigger was refused rather than queued.
 */
public enum TriggerResult {

    STARTED,
    ALREADY_RUNNING
}
