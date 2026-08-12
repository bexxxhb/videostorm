package de.videostorm.indexing.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The resolution of a movie's feature video, derived purely from the file's name — the {@code .nfo}
 * holds no resolution, so the filename is the only source. One of the recognised tokens
 * ({@code 2160}, {@code 1080}, {@code 720}, {@code 576}, each with an optional {@code p}) is lifted
 * where it stands alone (bounded by a separator or the ends of the name, case-insensitive), and the
 * result is always normalised to the {@code p}-suffixed form so the listing shows {@code 1080p} /
 * {@code 2160p} / {@code 720p} / {@code 576p} whether or not the filename carried the {@code p}.
 *
 * <p>This is deliberately separate from the show-side {@link TechnicalTokens}, which strips a bare
 * number only when it carries a {@code p}/{@code i} suffix: on the show side a bare {@code 720} or
 * {@code 1080} is indistinguishable from an episode number, so honouring it would cost real episodes.
 * A movie feature filename has no episode number to collide with, so a bare number is honoured here.
 */
public record Resolution(String display) {

    // 2160/1080/720/576 with an optional p, standing alone so a number inside a title word or a longer
    // run of digits (a year, "10800") is left untouched. The number is captured without the p suffix,
    // which is always re-added, so the display carries it whether the filename did or not.
    private static final Pattern TOKEN =
            Pattern.compile("(?i)(?<![a-z0-9])(2160|1080|720|576)p?(?![a-z0-9])");

    public Resolution {
        if (display == null || display.isBlank()) {
            throw new IllegalArgumentException("A resolution needs a display value");
        }
    }

    /** The normalised resolution of the given filename, or empty when no recognised token stands alone. */
    public static Optional<Resolution> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN.matcher(filename);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new Resolution(matcher.group(1) + "p"));
    }
}
