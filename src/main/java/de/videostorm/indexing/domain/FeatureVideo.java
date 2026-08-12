package de.videostorm.indexing.domain;

/**
 * The size rule that separates a real feature film from the clips that sit beside it. A directory is
 * only catalogued as a movie when it holds a recognised video (see {@link RecognizedVideo}) of at
 * least {@link #MIN_BYTES}; a trailer, sample or featurette below that size can never stand in for the
 * film. The threshold is a fixed 500 MB in decimal megabytes (500 × 1000 × 1000 bytes), matching how
 * media sizes are conventionally quoted.
 */
public final class FeatureVideo {

    /** The smallest a recognised video may be to count as a feature: 500 decimal MB. */
    public static final long MIN_BYTES = 500L * 1000 * 1000;

    private FeatureVideo() {
    }

    /** Whether {@code filename} is a recognised video and {@code sizeBytes} reaches the feature threshold. */
    public static boolean isFeature(String filename, long sizeBytes) {
        return RecognizedVideo.isVideoFile(filename) && sizeBytes >= MIN_BYTES;
    }
}
