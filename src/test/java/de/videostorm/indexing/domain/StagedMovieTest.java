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
        return new ParsedMovie("96 Hours - Taken 3", "Taken 3", year, ratings, genres,
                109, "A plot.", "Taken Collection", "133352", "tt2446042", null, "260346");
    }

    @Test
    void computesNormalisationSlugAndGenreStorage() {
        StagedMovie staged = StagedMovie.from(
                parsed(2014, List.of(), List.of("Action", "Thriller")),
                "/media/movies/Taken 3 (2014)",
                "<movie/>");

        assertThat(staged.title()).isEqualTo("96 Hours - Taken 3");
        assertThat(staged.year()).isEqualTo(2014);
        assertThat(staged.normalizedTitle()).isEqualTo("96 hours taken 3");
        assertThat(staged.normalizedOriginalTitle()).isEqualTo("taken 3");
        assertThat(staged.slug()).isEqualTo("96-hours-taken-3-2014");
        assertThat(staged.genresStorage()).isEqualTo("|Action|Thriller|");
        assertThat(staged.sourcePath()).isEqualTo("/media/movies/Taken 3 (2014)");
        assertThat(staged.rawNfo()).isEqualTo("<movie/>");
    }

    @Test
    void foldsAMissingYearToZeroAndAnEmptyGenreListToNoColumn() {
        StagedMovie staged = StagedMovie.from(parsed(null, List.of(), List.of()), "/p", "x");

        assertThat(staged.year()).isZero();
        assertThat(staged.slug()).isEqualTo("96-hours-taken-3-0");
        assertThat(staged.genresStorage()).isNull();
        assertThat(staged.normalizedOriginalTitle()).isEqualTo("taken 3");
    }

    @Test
    void picksTheProviderEmbyFlaggedDefaultForTheInlineColumns() {
        ParsedRating tmdb = new ParsedRating("themoviedb", new BigDecimal("6.3"), new BigDecimal("10"), 4200, true);
        ParsedRating imdb = new ParsedRating("imdb", new BigDecimal("6.0"), new BigDecimal("10"), 250000, false);

        StagedMovie staged = StagedMovie.from(parsed(2014, List.of(imdb, tmdb), List.of()), "/p", "x");

        assertThat(staged.defaultRating()).isEqualTo(tmdb);
        assertThat(staged.ratings()).containsExactly(imdb, tmdb);
    }

    @Test
    void fallsBackToTheFirstRatingWhenNoneIsFlaggedDefault() {
        ParsedRating first = new ParsedRating("imdb", new BigDecimal("6.0"), new BigDecimal("10"), 10, false);
        ParsedRating second = new ParsedRating("themoviedb", new BigDecimal("6.3"), new BigDecimal("10"), 20, false);

        StagedMovie staged = StagedMovie.from(parsed(2014, List.of(first, second), List.of()), "/p", "x");

        assertThat(staged.defaultRating()).isEqualTo(first);
    }

    @Test
    void hasNoInlineRatingWhenTheFilmIsUnrated() {
        StagedMovie staged = StagedMovie.from(parsed(2014, List.of(), List.of()), "/p", "x");

        assertThat(staged.defaultRating()).isNull();
        assertThat(staged.ratings()).isEmpty();
    }
}
