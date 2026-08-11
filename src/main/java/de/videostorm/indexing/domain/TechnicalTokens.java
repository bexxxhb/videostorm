package de.videostorm.indexing.domain;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cleans an episode filename before {@link EpisodeNumberParser} looks at it, so that release tags can
 * never be mistaken for a season or episode number. Two things are stripped:
 *
 * <ul>
 *   <li>the file extension;</li>
 *   <li>every technical token — a three-or-four-digit resolution carrying a {@code p} or {@code i}
 *       suffix ({@code 1080p}, {@code 720p}, {@code 1080i}), plus a fixed list of codec, source and
 *       audio tokens.</li>
 * </ul>
 *
 * <p>A bare resolution number with no {@code p}/{@code i} suffix ({@code 720}, {@code 1080}) is
 * deliberately <em>not</em> stripped: on the show side it is indistinguishable from the three-digit
 * episode form, so removing it would cost real episode numbers. Tokens are matched case-insensitively
 * and only where they stand alone (bounded by a separator or the ends of the name), so a token that
 * happens to be a substring of a title word is left untouched.
 */
public final class TechnicalTokens {

    /** Codec, source and audio tags Emby and scene releases append; matched whole and case-insensitively. */
    private static final List<String> CODEC_TOKENS = List.of(
            "x264", "x265", "h264", "h265", "HEVC", "AVC", "AAC", "AC3", "DTS",
            "BluRay", "BDRip", "WEB-DL", "WEBRip", "HDTV", "REMUX", "PROPER", "10bit", "HDR");

    // A token stands alone only when neither neighbour is alphanumeric, so "DTS" is stripped from
    // "Show.DTS.mkv" but preserved inside "DTShow"; the same guard keeps a bare "1080" (no p/i) intact.
    private static final Pattern RESOLUTION =
            Pattern.compile("(?i)(?<![a-z0-9])[0-9]{3,4}[pi](?![a-z0-9])");

    private static final Pattern CODECS = Pattern.compile(
            CODEC_TOKENS.stream().map(Pattern::quote).collect(Collectors.joining("|", "(?<![A-Za-z0-9])(?:", ")(?![A-Za-z0-9])")),
            Pattern.CASE_INSENSITIVE);

    private TechnicalTokens() {
    }

    /** The filename with its extension and every technical token removed; leftover separators are harmless. */
    public static String strip(String filename) {
        String stem = stripExtension(filename);
        stem = RESOLUTION.matcher(stem).replaceAll("");
        stem = CODECS.matcher(stem).replaceAll("");
        return stem;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }
}
