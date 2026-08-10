package de.videostorm.indexing.application.port.in;

import de.videostorm.indexing.domain.IndexingRun;

import java.util.List;
import java.util.Optional;

/** What the maintenance page reads: the active run, if any, and a short history of recent runs. */
public interface IndexingStatus {

    Optional<IndexingRun> activeRun();

    List<IndexingRun> recentRuns();
}
