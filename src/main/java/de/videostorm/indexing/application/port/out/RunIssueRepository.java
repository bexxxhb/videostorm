package de.videostorm.indexing.application.port.out;

import de.videostorm.indexing.domain.RunIssue;

import java.util.List;

/**
 * Persists the issue detail a scan produced, attached to the run that produced it. Detail lives beside
 * the run history, never in the catalogue, so a re-index reports afresh without touching the films it
 * built.
 */
public interface RunIssueRepository {

    /** Records every issue against {@code runId}; a run with nothing questionable records nothing. */
    void record(long runId, List<RunIssue> issues);
}
