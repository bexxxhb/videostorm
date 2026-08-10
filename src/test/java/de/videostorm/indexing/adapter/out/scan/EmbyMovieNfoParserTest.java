package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.domain.ParsedMovie;
import de.videostorm.indexing.domain.ParsedRating;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parsing an Emby movie {@code .nfo} into {@link ParsedMovie}: every field the catalogue keeps is
 * mapped, several {@code <rating>} providers survive with the default one flagged, missing fields
 * fall back to blank rather than failing the entry, and the parser is hardened so a malicious file
 * can neither pull in an external DTD nor resolve an external entity.
 */
class EmbyMovieNfoParserTest {

    private final EmbyMovieNfoParser parser = new EmbyMovieNfoParser();

    @Test
    void mapsEveryKeptFieldOfAFullyScrapedFilm() {
        ParsedMovie movie = parser.parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <movie>
                  <title>96 Hours - Taken 3</title>
                  <originaltitle>Taken 3</originaltitle>
                  <year>2014</year>
                  <ratings>
                    <rating name="themoviedb" max="10" default="true">
                      <value>6.3</value>
                      <votes>4200</votes>
                    </rating>
                    <rating name="imdb" max="10">
                      <value>6.0</value>
                      <votes>250000</votes>
                    </rating>
                  </ratings>
                  <genre>Action</genre>
                  <genre>Thriller</genre>
                  <runtime>109</runtime>
                  <plot>Ex-government operative Bryan Mills is accused of murder.</plot>
                  <set>
                    <name>Taken Collection</name>
                    <tmdbcolid>133352</tmdbcolid>
                  </set>
                  <uniqueid type="imdb">tt2446042</uniqueid>
                  <uniqueid type="tmdb">260346</uniqueid>
                  <uniqueid type="tvdb">98765</uniqueid>
                </movie>
                """);

        assertThat(movie.title()).isEqualTo("96 Hours - Taken 3");
        assertThat(movie.originalTitle()).isEqualTo("Taken 3");
        assertThat(movie.year()).isEqualTo(2014);
        assertThat(movie.genres()).containsExactly("Action", "Thriller");
        assertThat(movie.runtimeMinutes()).isEqualTo(109);
        assertThat(movie.plot()).isEqualTo("Ex-government operative Bryan Mills is accused of murder.");
        assertThat(movie.setName()).isEqualTo("Taken Collection");
        assertThat(movie.collectionId()).isEqualTo("133352");
        assertThat(movie.imdbId()).isEqualTo("tt2446042");
        assertThat(movie.tmdbId()).isEqualTo("260346");
        assertThat(movie.tvdbId()).isEqualTo("98765");

        assertThat(movie.ratings()).hasSize(2);
        ParsedRating themoviedb = movie.ratings().get(0);
        assertThat(themoviedb.source()).isEqualTo("themoviedb");
        assertThat(themoviedb.value()).isEqualByComparingTo(new BigDecimal("6.3"));
        assertThat(themoviedb.max()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(themoviedb.votes()).isEqualTo(4200);
        assertThat(themoviedb.isDefault()).isTrue();
        assertThat(movie.ratings().get(1).isDefault()).isFalse();
    }

    @Test
    void keepsAThinlyScrapedFilmWithEveryAbsentFieldBlank() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>Untitled Home Video</title>
                </movie>
                """);

        assertThat(movie.title()).isEqualTo("Untitled Home Video");
        assertThat(movie.originalTitle()).isNull();
        assertThat(movie.year()).isNull();
        assertThat(movie.runtimeMinutes()).isNull();
        assertThat(movie.plot()).isNull();
        assertThat(movie.setName()).isNull();
        assertThat(movie.collectionId()).isNull();
        assertThat(movie.imdbId()).isNull();
        assertThat(movie.ratings()).isEmpty();
        assertThat(movie.genres()).isEmpty();
    }

    @Test
    void costsOnlyTheFieldWhenANumberIsMalformed() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>Broken Year</title>
                  <year>MCMLXXXII</year>
                  <runtime>ninety</runtime>
                  <genre>Horror</genre>
                </movie>
                """);

        assertThat(movie.title()).isEqualTo("Broken Year");
        assertThat(movie.year()).isNull();
        assertThat(movie.runtimeMinutes()).isNull();
        assertThat(movie.genres()).containsExactly("Horror");
    }

    @Test
    void rejectsAStructurallyBrokenFileSoTheScanCanTreatItAsAbsent() {
        assertThatThrownBy(() -> parser.parse("<movie><title>Truncated"))
                .isInstanceOf(EmbyMovieNfoParser.NfoParseException.class);
        assertThatThrownBy(() -> parser.parse("not xml at all"))
                .isInstanceOf(EmbyMovieNfoParser.NfoParseException.class);
    }

    @Test
    void rejectsAWellFormedFileWhoseRootIsNotMovie() {
        assertThatThrownBy(() -> parser.parse("<tvshow><title>Wrong root</title></tvshow>"))
                .isInstanceOf(EmbyMovieNfoParser.NfoParseException.class);
    }

    @Test
    void readsAnOlderSetWrittenAsPlainText() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>Goldfinger</title>
                  <set>James Bond Collection</set>
                </movie>
                """);

        assertThat(movie.setName()).isEqualTo("James Bond Collection");
        assertThat(movie.collectionId()).isNull();
    }

    @Test
    void supportsAnInternalDtdAndExpandsItsEntities() {
        ParsedMovie movie = parser.parse("""
                <?xml version="1.0"?>
                <!DOCTYPE movie [ <!ENTITY studio "Pinewood"> ]>
                <movie>
                  <title>Shot at &studio;</title>
                </movie>
                """);

        assertThat(movie.title()).isEqualTo("Shot at Pinewood");
    }

    @Test
    void neverResolvesAnExternalEntity(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path secret = dir.resolve("secret.txt");
        java.nio.file.Files.writeString(secret, "TOP-SECRET-CONTENTS");
        String nfo = """
                <?xml version="1.0"?>
                <!DOCTYPE movie [ <!ENTITY xxe SYSTEM "file://%s"> ]>
                <movie>
                  <title>&xxe;</title>
                </movie>
                """.formatted(secret.toAbsolutePath());

        String leaked;
        try {
            leaked = parser.parse(nfo).title();
        } catch (RuntimeException expected) {
            // Refusing outright is an acceptable way to keep the file's contents out.
            return;
        }
        // An unresolved external entity leaves the title empty (null); the one thing that must never
        // happen is the file's contents appearing in it.
        assertThat(String.valueOf(leaked)).doesNotContain("TOP-SECRET-CONTENTS");
    }
}
