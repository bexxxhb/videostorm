package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.domain.ParsedRating;
import de.videostorm.indexing.domain.ParsedShow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parsing an Emby show {@code .nfo} into {@link ParsedShow}: every field the catalogue keeps is
 * mapped, the premiered date and status are carried through raw for the domain to shape, several
 * {@code <rating>} providers survive with the default one flagged, missing fields fall back to blank
 * rather than failing the entry, and the parser is hardened against external DTDs and entities.
 */
class EmbyShowNfoParserTest {

    private final EmbyShowNfoParser parser = new EmbyShowNfoParser();

    @Test
    void mapsEveryKeptFieldOfAFullyScrapedShow() {
        ParsedShow show = parser.parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <tvshow>
                  <title>Breaking Bad</title>
                  <originaltitle>Breaking Bad</originaltitle>
                  <premiered>2008-01-20</premiered>
                  <status>Ended</status>
                  <ratings>
                    <rating name="tvdb" max="10" default="true">
                      <value>9.5</value>
                      <votes>4200</votes>
                    </rating>
                    <rating name="imdb" max="10">
                      <value>9.4</value>
                      <votes>250000</votes>
                    </rating>
                  </ratings>
                  <genre>Crime</genre>
                  <genre>Drama</genre>
                  <plot>A chemistry teacher turns to cooking meth.</plot>
                  <uniqueid type="imdb">tt0903747</uniqueid>
                  <uniqueid type="tmdb">1396</uniqueid>
                  <uniqueid type="tvdb">81189</uniqueid>
                </tvshow>
                """);

        assertThat(show.title()).isEqualTo("Breaking Bad");
        assertThat(show.originalTitle()).isEqualTo("Breaking Bad");
        assertThat(show.premiered()).isEqualTo("2008-01-20");
        assertThat(show.status()).isEqualTo("Ended");
        assertThat(show.genres()).containsExactly("Crime", "Drama");
        assertThat(show.plot()).isEqualTo("A chemistry teacher turns to cooking meth.");
        assertThat(show.imdbId()).isEqualTo("tt0903747");
        assertThat(show.tmdbId()).isEqualTo("1396");
        assertThat(show.tvdbId()).isEqualTo("81189");

        assertThat(show.ratings()).hasSize(2);
        ParsedRating tvdb = show.ratings().get(0);
        assertThat(tvdb.source()).isEqualTo("tvdb");
        assertThat(tvdb.value()).isEqualByComparingTo(new BigDecimal("9.5"));
        assertThat(tvdb.max()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(tvdb.votes()).isEqualTo(4200);
        assertThat(tvdb.isDefault()).isTrue();
        assertThat(show.ratings().get(1).isDefault()).isFalse();
    }

    @Test
    void keepsAThinlyScrapedShowWithEveryAbsentFieldBlank() {
        ParsedShow show = parser.parse("""
                <tvshow>
                  <title>Untitled Pilot</title>
                </tvshow>
                """);

        assertThat(show.title()).isEqualTo("Untitled Pilot");
        assertThat(show.originalTitle()).isNull();
        assertThat(show.premiered()).isNull();
        assertThat(show.status()).isNull();
        assertThat(show.plot()).isNull();
        assertThat(show.imdbId()).isNull();
        assertThat(show.ratings()).isEmpty();
        assertThat(show.genres()).isEmpty();
    }

    @Test
    void ignoresAFlatTopLevelRatingSoShowsAreUnaffected() {
        // The flat-<rating> fallback is movie-only; a show with the same shape must import as before,
        // i.e. with no rating at all.
        ParsedShow show = parser.parse("""
                <tvshow>
                  <title>Flat Rated Show</title>
                  <rating>7.3</rating>
                  <votes>659</votes>
                </tvshow>
                """);

        assertThat(show.ratings()).isEmpty();
    }

    @Test
    void rejectsAStructurallyBrokenFileSoTheScanCanTreatItAsAbsent() {
        assertThatThrownBy(() -> parser.parse("<tvshow><title>Truncated"))
                .isInstanceOf(NfoParseException.class);
        assertThatThrownBy(() -> parser.parse("not xml at all"))
                .isInstanceOf(NfoParseException.class);
    }

    @Test
    void rejectsAWellFormedFileWhoseRootIsNotTvshow() {
        assertThatThrownBy(() -> parser.parse("<movie><title>Wrong root</title></movie>"))
                .isInstanceOf(NfoParseException.class);
    }

    @Test
    void neverResolvesAnExternalEntity(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path secret = dir.resolve("secret.txt");
        java.nio.file.Files.writeString(secret, "TOP-SECRET-CONTENTS");
        String nfo = """
                <?xml version="1.0"?>
                <!DOCTYPE tvshow [ <!ENTITY xxe SYSTEM "file://%s"> ]>
                <tvshow>
                  <title>&xxe;</title>
                </tvshow>
                """.formatted(secret.toAbsolutePath());

        String leaked;
        try {
            leaked = parser.parse(nfo).title();
        } catch (RuntimeException expected) {
            // Refusing outright is an acceptable way to keep the file's contents out.
            return;
        }
        assertThat(String.valueOf(leaked)).doesNotContain("TOP-SECRET-CONTENTS");
    }
}
