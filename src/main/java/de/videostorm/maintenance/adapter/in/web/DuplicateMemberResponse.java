package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.maintenance.domain.DuplicateMember;

/**
 * One member movie as the drill-down layer consumes it: its IMDb id, original title, file path and file
 * size in bytes (the client converts this to megabytes for display). Any absent attribute serializes as
 * JSON {@code null}, which the client renders as a dash.
 */
record DuplicateMemberResponse(String imdbId, String originalTitle, String filePath, Long sizeBytes) {

    static DuplicateMemberResponse from(DuplicateMember member) {
        return new DuplicateMemberResponse(
                member.imdbId().orElse(null),
                member.originalTitle().orElse(null),
                member.filePath().orElse(null),
                member.sizeBytes().orElse(null));
    }
}
