package de.videostorm.indexing.domain;

import de.videostorm.catalogue.domain.TitleNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses which video in a folder is the feature when several sit side by side, so a trailer or a
 * sample never gets catalogued in the film's place. The feature is the video whose name shares the
 * longest normalized common prefix with the folder name; ties break on the largest file, then on the
 * codepoint-ordered name so the choice is deterministic. Every other video is {@link #ignored()}.
 */
public record FeatureSelection(Video feature, List<Video> ignored) {

    /** A candidate video: its filename (with extension) and size in bytes, the tie-breaker. */
    public record Video(String filename, long sizeBytes) {
    }

    public FeatureSelection {
        if (feature == null) {
            throw new IllegalArgumentException("A feature must be chosen");
        }
        ignored = ignored == null ? List.of() : List.copyOf(ignored);
    }

    public static FeatureSelection choose(String folderName, List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            throw new IllegalArgumentException("Cannot choose a feature from no videos");
        }
        String normalizedFolder = TitleNormalizer.normalize(folderName);
        List<Video> byPreference = new ArrayList<>(videos);
        byPreference.sort(Comparator
                .comparingInt((Video video) -> commonPrefixLength(normalizedFolder, normalizedName(video))).reversed()
                .thenComparing(Comparator.comparingLong(Video::sizeBytes).reversed())
                .thenComparing(Video::filename));

        Video feature = byPreference.get(0);
        List<Video> ignored = byPreference.subList(1, byPreference.size()).stream()
                .sorted(Comparator.comparing(Video::filename))
                .toList();
        return new FeatureSelection(feature, ignored);
    }

    private static String normalizedName(Video video) {
        return TitleNormalizer.normalize(withoutExtension(video.filename()));
    }

    private static String withoutExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int shared = 0;
        while (shared < limit && left.charAt(shared) == right.charAt(shared)) {
            shared++;
        }
        return shared;
    }
}
