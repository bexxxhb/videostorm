package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.application.port.out.LibraryScan;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.sources.domain.SourceType;
import org.springframework.stereotype.Component;

/**
 * The scan placeholder for this scope: it visits nothing and reports nothing found, so a run
 * completes successfully without touching the disks. The real filesystem walk replaces this in a
 * later ticket.
 */
@Component
class StubLibraryScan implements LibraryScan {

    @Override
    public RunCounts scan(SourceType type) {
        return RunCounts.none();
    }
}
