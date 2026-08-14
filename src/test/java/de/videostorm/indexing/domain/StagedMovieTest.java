package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turning a {@link ParsedMovie} plus its folder context into a persistence-ready {@link StagedMovie}:
 * the normalised sort/search keys, the identity slug, the delimiter-padded genre column, the missing
 * year folded to zero, and the single default rating chosen for the inline listing columns while
 * every rating is carried through for the child table.
 */
class StagedMovieTest {

    private static ParsedMovie parsed(Integer year, List<ParsedRating> ratings, List<String> genres) {
        return new ParsedMovie("96 Hours - Taken 3", "Taken 3", year, ratings, genres, List.of(),
                109, "A plot.", "Taken Collection", "133352", "tt2446042", null, "260346");
    }

    @Test
    void computesNormalisationSlugAndGenreStorage() {
        StagedMovie staged = StagedMovie.from(
                parsed(2014, List.of(), List.of("Action", "Thriller")),
                "Taken 3 (2014)",
                "/media/movies/Taken 3 (2014)",
                "<movie/>",
                "1080p");

        assertThat(staged.title()).isEqualTo("96 Hours - Taken 3");
        assertThat(staged.derivedTitle()).isFalse();
        assertThat(staged.year()).isEqualTo(2014);
        assertThat(staged.resolution()).isEqualTo("1080p");
        assertThat(staged.normalizedTitle()).isEqualTo("96 hours taken 3");
        assertThat(staged.normalizedOriginalTitle()).isEqualTo("taken 3");
        assertThat(staged.slug()).isEqualTo("96-hours-taken-3-2014");
        assertThat(staged.genresStorage()).isEqualTo("|Action|Thriller|");
        assertThat(staged.sourcePath()).isEqualTo("/media/movies/Taken 3 (2014)");
        assertThat(staged.rawNfo()).isEqualTo("<movie/>");
    }

    @Test
    void foldsAMissingYearToZeroAndAnEmptyGenreListToNoColumn() {
        StagedMovie staged = StagedMovie.from(parsed(null, List.of(), List.of()), "Taken 3", "/p", "x", null);

        assertThat(staged.year()).isZero();
        assertThat(staged.slug()).isEqualTo("96-hours-taken-3-0");
        assertThat(staged.genresStorage()).isNull();
        assertThat(staged.normalizedOriginalTitle()).isEqualTo("taken 3");
    }

    @Test
    void fallsBackToTheFolderNameAndFlagsADerivedTitleWhenMetadataHasNone() {
        ParsedMovie noTitle = new ParsedMovie(null, null, null, List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null);

        StagedMovie staged = StagedMovie.from(noTitle, "The Blob (1958).mkv", "/p", "x", null);

        assertThat(staged.title()).isEqualTo("The Blob (1958)");
        assertThat(staged.derivedTitle()).isTrue();
        assertThat(staged.normalizedTitle()).isEqualTo("the blob 1958");
        // Year comes only from metadata, never the (1958) in the folder name.
        assertThat(staged.year()).isZero();
        assertThat(staged.slug()).isEqualTo("the-blob-1958-0");
    }

    @Test
    void derivesTheIdentityTitleFromTheOriginalTitleWherePresent() {
        StagedMovie staged = StagedMovie.from(parsed(2014, List.of(), List.of()), "Taken 3", "/p", "x", null);

        // Original title "Taken 3" wins over the display title "96 Hours - Taken 3".
        assertThat(staged.normalizedIdentityTitle()).isEqualTo("taken 3");
    }

    @Test
    void fallsBackToTheDisplayTitleForIdentityWhenNoOriginalTitle() {
        ParsedMovie noOriginal = new ParsedMovie("The Thing", null, 1982, List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null);

        StagedMovie staged = StagedMovie.from(noOriginal, "The Thing (1982)", "/p", "x", null);

        assertThat(staged.normalizedIdentityTitle()).isEqualTo("the thing");
    }

    @Test
    void identityTitleFallsThroughToTheFolderDerivedTitleWhenNothingWasParsed() {
        ParsedMovie nothing = new ParsedMovie(null, null, null, List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null);

        StagedMovie staged = StagedMovie.from(nothing, "The Blob (1958).mkv", "/p", "x", null);

        assertThat(staged.normalizedIdentityTitle()).isEqualTo("the blob 1958");
    }

    @Test
    void picksTheProviderEmbyFlaggedDefaultForTheInlineColumns() {
        ParsedRating tmdb = new ParsedRating("themoviedb", new BigDecimal("6.3"), new BigDecimal("10"), 4200, true);
        ParsedRating imdb = new ParsedRating("imdb", new BigDecimal("6.0"), new BigDecimal("10"), 250000, false);

        StagedMovie staged = StagedMovie.from(parsed(2014, List.of(imdb, tmdb), List.of()), "Taken 3", "/p", "x", null);

        assertThat(staged.defaultRating()).isEqualTo(tmdb);
        assertThat(staged.ratings()).containsExactly(imdb, tmdb);
    }

    @Test
    void fallsBackToTheFirstRatingWhenNoneIsFlaggedDefault() {
        ParsedRating first = new ParsedRating("imdb", new BigDecimal("6.0"), new BigDecimal("10"), 10, false);
        ParsedRating second = new ParsedRating("themoviedb", new BigDecimal("6.3"), new BigDecimal("10"), 20, false);

        StagedMovie staged = StagedMovie.from(parsed(2014, List.of(first, second), List.of()), "Taken 3", "/p", "x", null);

        assertThat(staged.defaultRating()).isEqualTo(first);
    }

    @Test
    void hasNoInlineRatingWhenTheFilmIsUnrated() {
        StagedMovie staged = StagedMovie.from(parsed(2014, List.of(), List.of()), "Taken 3", "/p", "x", null);

        assertThat(staged.defaultRating()).isNull();
        assertThat(staged.ratings()).isEmpty();
    }

    @Test
    void carriesTheParsedCastThroughInBillingOrder() {
        ParsedActor lead = new ParsedActor("Liam Neeson", "Bryan Mills", 0, "http://img/neeson.jpg", "3896");
        ParsedActor support = new ParsedActor("Famke Janssen", "Lenore", 1, null, null);
        ParsedMovie parsed = new ParsedMovie("96 Hours - Taken 3", "Taken 3", 2014,
                List.of(), List.of(), List.of(lead, support),
                109, "A plot.", "Taken Collection", "133352", "tt2446042", null, "260346");

        StagedMovie staged = StagedMovie.from(parsed, "Taken 3", "/p", "x", null);

        assertThat(staged.actors()).containsExactly(lead, support);
    }
}
