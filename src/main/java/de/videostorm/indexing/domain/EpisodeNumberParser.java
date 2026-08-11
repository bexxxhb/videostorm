package de.videostorm.indexing.domain;

import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a season and episode number from an episode filename. The filename is cleaned by
 * {@link TechnicalTokens} first — extension and release tags removed — then eight patterns are tried
 * <em>in a fixed order</em>, and the first to match wins. The set is closed: a filename that matches
 * none is unparseable, reported by the caller and never catalogued under a guessed number. Folders
 * never contribute a season; only the filename is looked at.
 *
 * <p>The episode is always the trailing two digits of the matched text; each pattern knows where its
 * own season sits. The matched text is taken from {@link Matcher#group()} rather than from index
 * arithmetic across the whole filename, and a pattern whose {@code .} wildcard matches a non-dot
 * character still parses (the season and episode are read by position within the match, never by
 * splitting on a literal dot that may not be there).
 */
public final class EpisodeNumberParser {

    // The Ep.NN form carries no season of its own, so by agreement every such episode is season one.
    private static final int FIRST_SEASON = 1;

    private EpisodeNumberParser() {
    }

    /**
     * The eight agreed patterns, in evaluation order — {@link #values()} preserves declaration order,
     * so the first constant that matches the cleaned filename is the one that wins. Each carries how to
     * read its season from the matched text; the episode is always the trailing two digits.
     */
    private enum EpisodePattern {

        /** {@code S03E01} — two-digit season after {@code S}, two-digit episode after {@code E}. */
        UPPER_SXX_EXX("S[0-9]{2}E[0-9]{2}", group -> twoDigitsAt(group, 1)),

        /** {@code s03e05} — the lowercase form. */
        LOWER_SXX_EXX("s[0-9]{2}e[0-9]{2}", group -> twoDigitsAt(group, 1)),

        /** {@code Ep.02} — always season one; the wildcard after {@code Ep} may be any character. */
        EP_NN("Ep.[0-9]{2}", group -> FIRST_SEASON),

        /** {@code 103} — first digit is the season, the last two the episode. */
        THREE_DIGITS("[0-9]{3}", group -> oneDigitAt(group, 0)),

        /** {@code 6.06} — single-digit season, dot, two-digit episode. */
        DIGIT_DOT_NN("[0-9]{1}\\.[0-9]{2}", group -> oneDigitAt(group, 0)),

        /** {@code s03.e11} — two-digit season, a wildcard separator, two-digit episode. */
        LOWER_SXX_SEP_EXX("s[0-9]{2}.e[0-9]{2}", group -> twoDigitsAt(group, 1)),

        /** {@code s4e05} — single-digit season, two-digit episode, no separator. */
        LOWER_SX_EXX("s[0-9]{1}e[0-9]{2}", group -> oneDigitAt(group, 1)),

        /** {@code s4.e05} — single-digit season, a wildcard separator, two-digit episode. */
        LOWER_SX_SEP_EXX("s[0-9]{1}.e[0-9]{2}", group -> oneDigitAt(group, 1));

        private final Pattern pattern;
        private final ToIntFunction<String> season;

        EpisodePattern(String regex, ToIntFunction<String> season) {
            this.pattern = Pattern.compile(regex);
            this.season = season;
        }

        Optional<ParsedEpisodeNumber> match(String cleaned) {
            Matcher matcher = pattern.matcher(cleaned);
            if (!matcher.find()) {
                return Optional.empty();
            }
            String group = matcher.group();
            return Optional.of(ParsedEpisodeNumber.of(season.applyAsInt(group), lastTwoDigits(group)));
        }
    }

    /** The season and episode read from {@code filename}, or empty when no pattern matches. */
    public static Optional<ParsedEpisodeNumber> parse(String filename) {
        String cleaned = TechnicalTokens.strip(filename);
        for (EpisodePattern candidate : EpisodePattern.values()) {
            Optional<ParsedEpisodeNumber> hit = candidate.match(cleaned);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    private static int twoDigitsAt(String group, int index) {
        return Integer.parseInt(group.substring(index, index + 2));
    }

    private static int oneDigitAt(String group, int index) {
        return Integer.parseInt(group.substring(index, index + 1));
    }

    private static int lastTwoDigits(String group) {
        return Integer.parseInt(group.substring(group.length() - 2));
    }
}
