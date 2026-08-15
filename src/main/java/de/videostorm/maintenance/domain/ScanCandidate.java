package de.videostorm.maintenance.domain;

import java.util.Optional;

/**
 * One movie as the duplicate scan reads it: only the three attributes that matter — the IMDb id and
 * original title it matches on, and the file path it is shown by. A blank or whitespace-only IMDb id
 * or original title counts as absent, so a movie "has" an attribute only when it carries a real value;
 * the {@link DuplicateScanner} then matches it on each attribute it actually has.
 *
 * @param imdbId        the IMDb id, present only when non-blank
 * @param originalTitle the original title, present only when non-blank
 * @param filePath      the movie's source file path, present when known
 */
public record ScanCandidate(Optional<String> imdbId, Optional<String> originalTitle, Optional<String> filePath) {

    public ScanCandidate {
        imdbId = blankToEmpty(imdbId);
        originalTitle = blankToEmpty(originalTitle);
        filePath = blankToEmpty(filePath);
    }

    /** Builds a candidate from the raw column values, treating {@code null} and blanks as absent. */
    public static ScanCandidate of(String imdbId, String originalTitle, String filePath) {
        return new ScanCandidate(
                Optional.ofNullable(imdbId), Optional.ofNullable(originalTitle), Optional.ofNullable(filePath));
    }

    /** The original title lowercased and trimmed — the key it matches other movies on. */
    Optional<String> normalizedOriginalTitle() {
        return originalTitle.map(title -> title.trim().toLowerCase());
    }

    private static Optional<String> blankToEmpty(Optional<String> value) {
        return value.filter(candidate -> !candidate.isBlank());
    }
}
