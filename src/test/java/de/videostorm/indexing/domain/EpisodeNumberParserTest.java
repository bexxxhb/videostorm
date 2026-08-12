package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Season and episode extraction from a filename, table-driven so every one of the eight agreed
 * patterns is pinned individually alongside the ordering, stripping and no-match rules. These are the
 * cheap, exhaustive domain tests the spec asks for; the filesystem seam exercises the same parser
 * against real files.
 */
class EpisodeNumberParserTest {

    @ParameterizedTest(name = "{0} -> S{1}E{2}")
    @CsvSource({
            // #1 S[0-9]{2}E[0-9]{2}
            "S03E01.mkv, 3, 1",
            // #2 s[0-9]{2}e[0-9]{2}
            "s03e05.mkv, 3, 5",
            // #3 Ep.[0-9]{2} — always season one
            "Ep.02.mkv, 1, 2",
            // #4 \d{3} — first digit season, last two episode
            "103.mkv, 1, 3",
            // #5 \d{1}\.\d{2}
            "6.06.mkv, 6, 6",
            // #6 s[0-9]{2}.e[0-9]{2}
            "s03.e11.mkv, 3, 11",
            // #7 s[0-9]{1}e[0-9]{2}
            "s4e05.mkv, 4, 5",
            // #8 s[0-9]{1}.e[0-9]{2}
            "s4.e05.mkv, 4, 5",
    })
    void extractsEachOfTheEightPatterns(String filename, int season, int episode) {
        assertThat(EpisodeNumberParser.parse(filename))
                .contains(ParsedEpisodeNumber.of(season, episode));
    }

    @Test
    void theEpDotFormAlwaysYieldsSeasonOneRegardlessOfTheWildcardCharacter() {
        // The '.' in the Ep.NN pattern is a wildcard; a non-dot separator must still parse, never throw.
        assertThat(EpisodeNumberParser.parse("Ep 07.mkv")).contains(ParsedEpisodeNumber.of(1, 7));
    }

    @Test
    void seasonZeroIsExtractedAsSpecials() {
        Optional<ParsedEpisodeNumber> parsed = EpisodeNumberParser.parse("S00E03.mkv");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().season().isSpecials()).isTrue();
        assertThat(parsed.get().episode()).isEqualTo(3);
    }

    @Test
    void stripsTechnicalTokensBeforeMatchingSoAResolutionTagNeverBecomesTheNumber() {
        assertThat(EpisodeNumberParser.parse("Firefly.720p.S01E01.x264.mkv"))
                .contains(ParsedEpisodeNumber.of(1, 1));
    }

    @Test
    void earlierPatternsWinWhenSeveralCouldMatch() {
        // "S03E01" is pattern #1; the bare "03"/"01" never fall through to a later rule.
        assertThat(EpisodeNumberParser.parse("Show.S03E01.mkv"))
                .contains(ParsedEpisodeNumber.of(3, 1));
    }

    @Test
    void aYearInTheFilenameMisParsesViaTheThreeDigitRuleAsTheSpecAccepts() {
        // Documented trade-off: with the order fixed, \d{3} matches "201" in the year before the
        // s03.e11 rule is reached, so this resolves to S2E01 rather than S3E11.
        assertThat(EpisodeNumberParser.parse("Show.2014.s03.e11.mkv"))
                .contains(ParsedEpisodeNumber.of(2, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pilot.mkv", "Behind the Scenes.mkv", "trailer.mkv", "readme.nfo"})
    void aFilenameMatchingNoPatternIsUnparseable(String filename) {
        assertThat(EpisodeNumberParser.parse(filename)).isEmpty();
    }
}
