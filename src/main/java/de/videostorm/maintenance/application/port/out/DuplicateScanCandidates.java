package de.videostorm.maintenance.application.port.out;

import de.videostorm.maintenance.domain.ScanCandidate;

import java.util.List;

/** Supplies every catalogued movie to the scan, reduced to the attributes duplicate detection needs. */
public interface DuplicateScanCandidates {

    List<ScanCandidate> all();
}
