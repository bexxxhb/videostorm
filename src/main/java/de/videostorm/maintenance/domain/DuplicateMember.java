package de.videostorm.maintenance.domain;

import java.util.Optional;

/**
 * One movie inside a duplicate group, carrying exactly what the drill-down layer lists for it: its
 * IMDb id, original title and file path. These are the movie's own raw values as the scan saw them —
 * a snapshot, so the persisted run stays meaningful even after the catalogue changes.
 */
public record DuplicateMember(Optional<String> imdbId, Optional<String> originalTitle, Optional<String> filePath) {

    static DuplicateMember from(ScanCandidate candidate) {
        return new DuplicateMember(candidate.imdbId(), candidate.originalTitle(), candidate.filePath());
    }
}
