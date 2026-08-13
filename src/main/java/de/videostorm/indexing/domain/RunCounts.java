package de.videostorm.indexing.domain;

/**
 * What a run tallied: how many catalogue entries it {@code found} on disk, how many it
 * {@code indexed} into the catalogue, how many candidate directories it {@code skipped} for holding no
 * feature large enough to be a movie, and how many indexed entries had {@code missingData} — tracked
 * fields that came back thin. All four are non-negative. A run that has not finished, or the current
 * stub scan that visits nothing, carries {@link #none()}.
 *
 * <p>Only the movie scan skips: a directory that holds recognised video(s) but none reaching the
 * feature-size threshold ({@link FeatureVideo#MIN_BYTES}) is not a movie folder, so it is counted here
 * rather than catalogued. The show scan never skips, so it uses the {@link #RunCounts(int, int)}
 * two-argument form and leaves {@code skipped} zero.
 *
 * <p>{@code missingData} is not tallied by the scan itself but derived from the run's
 * {@link RunIssueType#MISSING_FIELD} issues at finalisation (see
 * {@link RunGapSummary#distinctMissingDataEntries(java.util.List)}) and folded in with
 * {@link #withMissingData(int)}; the scan-produced counts leave it zero.
 */
public record RunCounts(int found, int indexed, int skipped, int missingData) {

    public RunCounts {
        if (found < 0 || indexed < 0 || skipped < 0 || missingData < 0) {
            throw new IllegalArgumentException(
                    "Run counts must not be negative: found=" + found + ", indexed=" + indexed
                            + ", skipped=" + skipped + ", missingData=" + missingData);
        }
    }

    /** A tally with no missing-data count yet — the shape the scan produces before finalisation. */
    public RunCounts(int found, int indexed, int skipped) {
        this(found, indexed, skipped, 0);
    }

    /** A run with nothing skipped — the show scan and any tally that only counts found/indexed. */
    public RunCounts(int found, int indexed) {
        this(found, indexed, 0, 0);
    }

    public static RunCounts none() {
        return new RunCounts(0, 0, 0, 0);
    }

    /** The same tally with its distinct-entry missing-data count set — computed when a run finalises. */
    public RunCounts withMissingData(int missingData) {
        return new RunCounts(found, indexed, skipped, missingData);
    }
}
