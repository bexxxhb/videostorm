package de.videostorm.maintenance.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Finds duplicate movies. Two movies are duplicates when they match on <em>either</em> attribute —
 * exact IMDb id, or original title after lowercasing and trimming — and the two criteria are unioned:
 * a movie is grouped independently under each attribute it carries a shared value for, so it may
 * appear in more than one group. A movie is considered for a criterion only when it actually has that
 * attribute; one with neither an IMDb id nor an original title takes part in no group at all.
 *
 * <p>Results are grouped per shared value, never pairwise: one group holds <em>every</em> movie
 * carrying a given value, and only values shared by two or more movies form a group. Groups are
 * ordered IMDb-id first then original-title, each by shared value; members keep the order they were
 * scanned in — so the same catalogue always yields the same run.
 */
public class DuplicateScanner {

    public List<DuplicateGroup> scan(List<ScanCandidate> candidates) {
        List<DuplicateGroup> groups = new ArrayList<>();
        groups.addAll(groupBy(candidates, DuplicateCriterion.IMDB_ID, ScanCandidate::imdbId));
        groups.addAll(groupBy(candidates, DuplicateCriterion.ORIGINAL_TITLE, ScanCandidate::normalizedOriginalTitle));
        return groups;
    }

    /**
     * Buckets the candidates that carry {@code key} by its value and keeps every bucket of two or
     * more, sorted by shared value. Candidates without the attribute drop out silently.
     */
    private static List<DuplicateGroup> groupBy(List<ScanCandidate> candidates, DuplicateCriterion criterion,
                                                Function<ScanCandidate, Optional<String>> key) {
        Map<String, List<ScanCandidate>> byValue = new LinkedHashMap<>();
        for (ScanCandidate candidate : candidates) {
            key.apply(candidate).ifPresent(value ->
                    byValue.computeIfAbsent(value, ignored -> new ArrayList<>()).add(candidate));
        }
        return byValue.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DuplicateGroup(criterion, entry.getKey(),
                        entry.getValue().stream().map(DuplicateMember::from).toList()))
                .toList();
    }
}
