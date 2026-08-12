package de.videostorm.indexing.application.port.out;

import de.videostorm.indexing.domain.RunIssue;

import java.util.List;

/**
 * Persists the issue detail a scan produced, attached to the run that produced it. Detail lives beside
 * the run history, never in the catalogue, so a re-index reports afresh without touching the films it
 * built.
 */
public interface RunIssueRepository {

    /**
     * How many runs keep their issue detail. The write side prunes beyond this as runs settle and the
     * read side offers a download only within it, so the retention policy lives in one place.
     */
    int DETAIL_RETENTION_LIMIT = 10;

    /** Records every issue against {@code runId}; a run with nothing questionable records nothing. */
    void record(long runId, List<RunIssue> issues);

    /** The issue detail of one run, for the gap counts and the CSV export; empty when none survives. */
    List<RunIssue> findByRun(long runId);

    /**
     * Prunes issue detail so only the {@code retainedRuns} most recently started runs keep theirs;
     * the run summaries themselves are never touched, so the history survives indefinitely while its
     * detail does not.
     */
    void pruneDetailBeyond(int retainedRuns);
}
