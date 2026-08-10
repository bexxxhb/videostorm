package de.videostorm.sources.domain;

/**
 * A normalized, absolute filesystem location configured as a source of media.
 *
 * <p>Configuration supplies raw strings. Construction trims surrounding whitespace, requires the
 * result to be absolute, and strips any trailing slash, so that {@code /media/movies} and
 * {@code /media/movies/} denote the same path. The raw value is exposed only to the startup log
 * and the indexer; it is never rendered in the UI.
 */
public record SourcePath(String value) {

    public SourcePath {
        if (value == null) {
            throw new IllegalArgumentException("Source path must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Source path must not be blank");
        }
        if (!trimmed.startsWith("/")) {
            throw new IllegalArgumentException("Source path is not absolute: '" + trimmed + "'");
        }
        value = stripTrailingSlashes(trimmed);
    }

    public static SourcePath of(String raw) {
        return new SourcePath(raw);
    }

    private static String stripTrailingSlashes(String path) {
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    /**
     * Whether this path is a strict ancestor of {@code other} — the same directory tree, one path
     * nested inside the other. Equal paths are not prefixes of one another; that is duplication,
     * which callers detect separately. Comparison is on whole path segments, so {@code /media/mov}
     * is not treated as a prefix of {@code /media/movies}, and the filesystem root {@code /} is an
     * ancestor of every other absolute path.
     */
    public boolean isPrefixOf(SourcePath other) {
        if (value.equals(other.value)) {
            return false;
        }
        String withSeparator = value.equals("/") ? "/" : value + "/";
        return other.value.startsWith(withSeparator);
    }
}
