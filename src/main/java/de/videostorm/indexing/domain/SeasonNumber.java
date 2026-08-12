package de.videostorm.indexing.domain;

/**
 * A season number extracted from an episode filename. Season {@code 0} is the Specials season — the
 * bucket Emby uses for extras, pilots and one-offs that sit outside the numbered run — so the value
 * object carries that meaning rather than leaving {@code 0} as a bare magic number. Negative seasons
 * are meaningless and rejected; the parser never produces one.
 */
public record SeasonNumber(int value) {

    /** The season Emby reserves for specials; {@link #isSpecials()} names it so callers need no literal. */
    public static final int SPECIALS = 0;

    public SeasonNumber {
        if (value < 0) {
            throw new IllegalArgumentException("Season number must not be negative: " + value);
        }
    }

    public boolean isSpecials() {
        return value == SPECIALS;
    }
}
