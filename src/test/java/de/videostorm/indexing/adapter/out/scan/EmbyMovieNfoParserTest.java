package de.videostorm.indexing.adapter.out.scan;

import de.videostorm.indexing.domain.ParsedActor;
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
    void readsAFlatTopLevelRatingAsTheDefaultTmdbScore() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>13 Assassins</title>
                  <year>2010</year>
                  <rating>7.3</rating>
                  <votes>659</votes>
                </movie>
                """);

        assertThat(movie.ratings()).hasSize(1);
        ParsedRating flat = movie.ratings().get(0);
        assertThat(flat.source()).isEqualTo("TMDB");
        assertThat(flat.value()).isEqualByComparingTo(new BigDecimal("7.3"));
        assertThat(flat.max()).isNull();
        assertThat(flat.votes()).isEqualTo(659);
        assertThat(flat.isDefault()).isTrue();
    }

    @Test
    void prefersTheStructuredRatingsBlockOverAFlatRating() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>13 Assassins</title>
                  <rating>7.3</rating>
                  <votes>659</votes>
                  <ratings>
                    <rating name="themoviedb" max="10" default="true">
                      <value>6.3</value>
                      <votes>4200</votes>
                    </rating>
                  </ratings>
                </movie>
                """);

        assertThat(movie.ratings()).hasSize(1);
        ParsedRating structured = movie.ratings().get(0);
        assertThat(structured.source()).isEqualTo("themoviedb");
        assertThat(structured.value()).isEqualByComparingTo(new BigDecimal("6.3"));
        assertThat(structured.max()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(structured.votes()).isEqualTo(4200);
    }

    @Test
    void dropsAMalformedFlatRatingToAnEmptyRatingCell() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>No Score</title>
                  <rating>N/A</rating>
                </movie>
                """);

        assertThat(movie.ratings()).isEmpty();
    }

    @Test
    void extractsTheLeadingMinutesFromARuntimeWithATrailingSuffix() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>Example</title>
                  <runtime>90 min (25 fps)</runtime>
                </movie>
                """);

        assertThat(movie.runtimeMinutes()).isEqualTo(90);
    }

    @Test
    void keepsAThousandsSeparatorInALeadingRuntimeRatherThanTruncatingIt() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>Very Long Cut</title>
                  <runtime>1,234 min</runtime>
                </movie>
                """);

        assertThat(movie.runtimeMinutes()).isEqualTo(1234);
    }

    @Test
    void dropsARuntimeWithNoLeadingDigitsToAnEmptyCell() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>No Runtime</title>
                  <runtime>N/A</runtime>
                </movie>
                """);

        assertThat(movie.runtimeMinutes()).isNull();
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
                .isInstanceOf(NfoParseException.class);
        assertThatThrownBy(() -> parser.parse("not xml at all"))
                .isInstanceOf(NfoParseException.class);
    }

    @Test
    void rejectsAWellFormedFileWhoseRootIsNotMovie() {
        assertThatThrownBy(() -> parser.parse("<tvshow><title>Wrong root</title></tvshow>"))
                .isInstanceOf(NfoParseException.class);
    }

    @Test
    void extractsTheCastPreservingBillingOrderWithTheTmdbPersonId() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>Taken 3</title>
                  <actor>
                    <name>Liam Neeson</name>
                    <role>Bryan Mills</role>
                    <order>0</order>
                    <thumb>http://image/neeson.jpg</thumb>
                    <tmdbid>3896</tmdbid>
                  </actor>
                  <actor>
                    <name>Famke Janssen</name>
                    <role>Lenore</role>
                    <order>1</order>
                  </actor>
                </movie>
                """);

        assertThat(movie.actors()).hasSize(2);
        ParsedActor lead = movie.actors().get(0);
        assertThat(lead.name()).isEqualTo("Liam Neeson");
        assertThat(lead.role()).isEqualTo("Bryan Mills");
        assertThat(lead.order()).isZero();
        assertThat(lead.thumb()).isEqualTo("http://image/neeson.jpg");
        assertThat(lead.tmdbId()).isEqualTo("3896");
        // The supporting actor omitted thumb and tmdbid; both are left null, only that field costs.
        ParsedActor support = movie.actors().get(1);
        assertThat(support.name()).isEqualTo("Famke Janssen");
        assertThat(support.order()).isEqualTo(1);
        assertThat(support.thumb()).isNull();
        assertThat(support.tmdbId()).isNull();
    }

    @Test
    void hasAnEmptyCastWhenTheFilmListsNoActors() {
        ParsedMovie movie = parser.parse("<movie><title>Castless</title></movie>");

        assertThat(movie.actors()).isEmpty();
    }

    @Test
    void skipsAnActorWithABlankOrMissingNameWithoutFailingTheEntry() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>One Named Actor</title>
                  <actor><role>Nobody</role><order>0</order></actor>
                  <actor><name>  </name><role>Blank</role></actor>
                  <actor><name>Real Person</name></actor>
                </movie>
                """);

        // The nameless and blank-named entries are dropped; only the named actor survives.
        assertThat(movie.actors()).extracting(ParsedActor::name).containsExactly("Real Person");
    }

    @Test
    void defaultsBillingOrderToDocumentPositionWhenTheOrderElementIsAbsent() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>Unordered Cast</title>
                  <actor><name>First Billed</name></actor>
                  <actor><name>Second Billed</name></actor>
                </movie>
                """);

        assertThat(movie.actors()).extracting(ParsedActor::name)
                .containsExactly("First Billed", "Second Billed");
        assertThat(movie.actors()).extracting(ParsedActor::order).containsExactly(0, 1);
    }

    @Test
    void preservesExplicitOrderValuesEvenWhenTheyRunAgainstDocumentOrder() {
        ParsedMovie movie = parser.parse("""
                <movie>
                  <title>Explicitly Ordered</title>
                  <actor><name>Listed First</name><order>2</order></actor>
                  <actor><name>Listed Second</name><order>0</order></actor>
                </movie>
                """);

        // The list stays in document order; each actor keeps the billing position the file gave it.
        assertThat(movie.actors()).extracting(ParsedActor::name)
                .containsExactly("Listed First", "Listed Second");
        assertThat(movie.actors()).extracting(ParsedActor::order).containsExactly(2, 0);
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
