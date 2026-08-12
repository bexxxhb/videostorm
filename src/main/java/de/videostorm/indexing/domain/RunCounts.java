package de.videostorm.indexing.domain;

/**
 * What a run tallied: how many catalogue entries it {@code found} on disk, how many it
 * {@code indexed} into the catalogue, and how many candidate directories it {@code skipped} for
 * holding no feature large enough to be a movie. All three are non-negative. A run that has not
 * finished, or the current stub scan that visits nothing, carries {@link #none()}.
 *
 * <p>Only the movie scan skips: a directory that holds recognised video(s) but none reaching the
 * feature-size threshold ({@link FeatureVideo#MIN_BYTES}) is not a movie folder, so it is counted here
 * rather than catalogued. The show scan never skips, so it uses the {@link #RunCounts(int, int)}
 * two-argument form and leaves {@code skipped} zero.
 */
public record RunCounts(int found, int indexed, int skipped) {

    public RunCounts {
        if (found < 0 || indexed < 0 || skipped < 0) {
            throw new IllegalArgumentException(
                    "Run counts must not be negative: found=" + found + ", indexed=" + indexed
                            + ", skipped=" + skipped);
        }
    }

    /** A run with nothing skipped — the show scan and any tally that only counts found/indexed. */
    public RunCounts(int found, int indexed) {
        this(found, indexed, 0);
    }

    public static RunCounts none() {
        return new RunCounts(0, 0, 0);
    }
}
