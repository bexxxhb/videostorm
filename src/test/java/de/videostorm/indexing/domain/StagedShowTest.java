package de.videostorm.indexing.domain;

import de.videostorm.catalogue.domain.ShowStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turning a {@link ParsedShow} plus its folder context into a persistence-ready {@link StagedShow}:
 * the normalised sort/search keys, the identity slug, the delimiter-padded genre column, the year read
 * from the premiered date, the mapped status, and the single default rating chosen for the inline
 * listing columns while every rating is carried through for the child table.
 */
class StagedShowTest {

    private static ParsedShow parsed(String premiered, String status,
                                     List<ParsedRating> ratings, List<String> genres) {
        return new ParsedShow("Breaking Bad", "Breaking Bad", premiered, ratings, genres,
                "A plot.", status, "tt0903747", "81189", "1396");
    }

    @Test
    void computesNormalisationSlugStatusYearAndGenreStorage() {
        StagedShow staged = StagedShow.from(
                parsed("2008-01-20", "Ended", List.of(), List.of("Crime", "Drama")),
                "Breaking Bad (2008)",
                "/media/shows/Breaking Bad (2008)",
                "<tvshow/>");

        assertThat(staged.title()).isEqualTo("Breaking Bad");
        assertThat(staged.derivedTitle()).isFalse();
        assertThat(staged.year()).isEqualTo(2008);
        assertThat(staged.status()).isEqualTo(ShowStatus.ENDED);
        assertThat(staged.normalizedTitle()).isEqualTo("breaking bad");
        assertThat(staged.normalizedOriginalTitle()).isEqualTo("breaking bad");
        assertThat(staged.slug()).isEqualTo("breaking-bad-2008");
        assertThat(staged.genresStorage()).isEqualTo("|Crime|Drama|");
        assertThat(staged.plot()).isEqualTo("A plot.");
        assertThat(staged.imdbId()).isEqualTo("tt0903747");
        assertThat(staged.tvdbId()).isEqualTo("81189");
        assertThat(staged.tmdbId()).isEqualTo("1396");
        assertThat(staged.sourcePath()).isEqualTo("/media/shows/Breaking Bad (2008)");
        assertThat(staged.rawNfo()).isEqualTo("<tvshow/>");
    }

    @Test
    void foldsAMissingPremieredToZeroYearAnEmptyGenreListToNoColumnAndAbsentStatusToUnknown() {
        StagedShow staged = StagedShow.from(parsed(null, null, List.of(), List.of()), "Breaking Bad", "/p", "x");

        assertThat(staged.year()).isZero();
        assertThat(staged.status()).isEqualTo(ShowStatus.UNKNOWN);
        assertThat(staged.slug()).isEqualTo("breaking-bad-0");
        assertThat(staged.genresStorage()).isNull();
    }

    @Test
    void fallsBackToTheFolderNameAndFlagsADerivedTitleWhenMetadataHasNone() {
        ParsedShow noTitle = new ParsedShow(null, null, null, List.of(), List.of(),
                null, null, null, null, null);

        StagedShow staged = StagedShow.from(noTitle, "The Wire (2002)", "/p", "x");

        assertThat(staged.title()).isEqualTo("The Wire (2002)");
        assertThat(staged.derivedTitle()).isTrue();
        assertThat(staged.normalizedTitle()).isEqualTo("the wire 2002");
        // Year comes only from the premiered date, never the (2002) in the folder name.
        assertThat(staged.year()).isZero();
        assertThat(staged.slug()).isEqualTo("the-wire-2002-0");
    }

    @Test
    void picksTheProviderEmbyFlaggedDefaultForTheInlineColumnsAndCarriesEveryRating() {
        ParsedRating tvdb = new ParsedRating("tvdb", new BigDecimal("9.5"), new BigDecimal("10"), 4200, true);
        ParsedRating imdb = new ParsedRating("imdb", new BigDecimal("9.4"), new BigDecimal("10"), 250000, false);

        StagedShow staged = StagedShow.from(parsed("2008", "Ended", List.of(imdb, tvdb), List.of()), "Breaking Bad", "/p", "x");

        assertThat(staged.defaultRating()).isEqualTo(tvdb);
        assertThat(staged.ratings()).containsExactly(imdb, tvdb);
    }

    @Test
    void hasNoInlineRatingWhenTheShowIsUnrated() {
        StagedShow staged = StagedShow.from(parsed("2008", "Continuing", List.of(), List.of()), "Breaking Bad", "/p", "x");

        assertThat(staged.defaultRating()).isNull();
        assertThat(staged.ratings()).isEmpty();
        assertThat(staged.status()).isEqualTo(ShowStatus.CONTINUING);
    }
}
