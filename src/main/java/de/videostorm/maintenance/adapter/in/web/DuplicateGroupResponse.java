package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.maintenance.domain.DuplicateGroup;

import java.util.List;

/**
 * One duplicate group as the drill-down layer consumes it: the criterion label, the shared value, and
 * the member movies carrying it.
 */
record DuplicateGroupResponse(String criterion, String sharedValue, List<DuplicateMemberResponse> members) {

    static DuplicateGroupResponse from(DuplicateGroup group) {
        return new DuplicateGroupResponse(
                group.criterion().label(),
                group.sharedValue(),
                group.members().stream().map(DuplicateMemberResponse::from).toList());
    }
}
