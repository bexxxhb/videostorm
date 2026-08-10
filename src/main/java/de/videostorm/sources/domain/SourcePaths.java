package de.videostorm.sources.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The validated set of configured source paths, grouped by {@link SourceType}.
 *
 * <p>Built from raw configuration strings: each entry is normalized into a {@link SourcePath},
 * then the whole set — across both types — is checked so that no path appears twice and no path
 * is nested inside another. Either fault is a misconfiguration that aborts application startup,
 * naming the offending pair, so a nested mount can never silently double-index and fill the
 * report with false duplicates. A type with no configured paths is allowed and does not prevent
 * startup.
 */
public final class SourcePaths {

    private final Map<SourceType, List<SourcePath>> byType;

    private SourcePaths(Map<SourceType, List<SourcePath>> byType) {
        this.byType = byType;
    }

    public static SourcePaths fromRaw(Map<SourceType, List<String>> raw) {
        Map<SourceType, List<SourcePath>> byType = new EnumMap<>(SourceType.class);
        List<SourcePath> all = new ArrayList<>();
        for (SourceType type : SourceType.values()) {
            List<SourcePath> paths = new ArrayList<>();
            for (String entry : raw.getOrDefault(type, List.of())) {
                SourcePath path = SourcePath.of(entry);
                paths.add(path);
                all.add(path);
            }
            byType.put(type, List.copyOf(paths));
        }
        rejectOverlaps(all);
        return new SourcePaths(byType);
    }

    private static void rejectOverlaps(List<SourcePath> all) {
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                SourcePath a = all.get(i);
                SourcePath b = all.get(j);
                if (a.equals(b)) {
                    throw new IllegalArgumentException(
                            "Source path appears twice: '" + a.value() + "'");
                }
                if (a.isPrefixOf(b) || b.isPrefixOf(a)) {
                    throw new IllegalArgumentException(
                            "Source paths overlap: '" + a.value() + "' and '" + b.value() + "'");
                }
            }
        }
    }

    public List<SourcePath> pathsFor(SourceType type) {
        return byType.getOrDefault(type, List.of());
    }

    public boolean hasPathsFor(SourceType type) {
        return !pathsFor(type).isEmpty();
    }
}
