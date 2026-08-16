package de.videostorm.maintenance.domain;

import java.util.List;

/**
 * The movies that share one value under one criterion — e.g. every movie carrying the IMDb id
 * {@code tt0111161}, or every movie whose original title normalizes to {@code the matrix}. A group
 * always holds two or more members; a single movie is not a duplicate of anything.
 *
 * @param criterion   the attribute the members share
 * @param sharedValue the value they share — the IMDb id verbatim, or the normalized original title
 * @param members     the movies carrying that value, two or more
 */
public record DuplicateGroup(DuplicateCriterion criterion, String sharedValue, List<DuplicateMember> members) {

    public DuplicateGroup {
        members = List.copyOf(members);
    }
}
