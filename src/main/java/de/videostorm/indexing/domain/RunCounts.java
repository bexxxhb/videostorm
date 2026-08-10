package de.videostorm.indexing.domain;

/**
 * What a run tallied: how many catalogue entries it {@code found} on disk and how many it
 * {@code indexed} into the catalogue. Both are non-negative. A run that has not finished, or the
 * current stub scan that visits nothing, carries {@link #none()}.
 */
public record RunCounts(int found, int indexed) {

    public RunCounts {
        if (found < 0 || indexed < 0) {
            throw new IllegalArgumentException(
                    "Run counts must not be negative: found=" + found + ", indexed=" + indexed);
        }
    }

    public static RunCounts none() {
        return new RunCounts(0, 0);
    }
}
