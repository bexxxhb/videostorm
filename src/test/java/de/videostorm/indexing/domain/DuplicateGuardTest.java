package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run's own duplicate detection: two folders that resolve to the same film — the same normalised
 * identity title with the same year, or the same imdb id — are caught as the run walks the library, so
 * the second is skipped and both locations are kept while genuinely different films still get through.
 */
class DuplicateGuardTest {

    private static StagedMovie movie(String title, String originalTitle, int year, String imdbId) {
        ParsedMovie parsed = new ParsedMovie(title, originalTitle, year == 0 ? null : year,
                List.of(), List.of(), null, null, null, null, imdbId, null, null);
        return StagedMovie.from(parsed, title, "/ignored", null);
    }

    @Test
    void theFirstFilmToClaimAnIdentityIsAdmitted() {
        DuplicateGuard guard = new DuplicateGuard();

        assertThat(guard.claim(movie("Heat", null, 1995, null), "/m/Heat")).isEmpty();
    }

    @Test
    void aSecondFolderWithTheSameIdentityTitleAndYearIsRejectedWithTheFirstPath() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.claim(movie("Heat", null, 1995, null), "/m/Heat");

        Optional<String> original = guard.claim(movie("Heat", null, 1995, null), "/m/Heat (copy)");

        assertThat(original).contains("/m/Heat");
    }

    @Test
    void sameTitleButADifferentYearIsADifferentFilm() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.claim(movie("The Thing", null, 1982, null), "/m/Thing 1982");

        assertThat(guard.claim(movie("The Thing", null, 2011, null), "/m/Thing 2011")).isEmpty();
    }

    @Test
    void identityIgnoresPunctuationCapitalisationAndDiacritics() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.claim(movie("Amélie", null, 2001, null), "/m/Amelie");

        Optional<String> original = guard.claim(movie("amelie!!!", null, 2001, null), "/m/Amelie (dup)");

        assertThat(original).contains("/m/Amelie");
    }

    @Test
    void identityIsTheOriginalTitleWhenPresentSoRetitledDisplayStillClashes() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.claim(movie("96 Hours - Taken 3", "Taken 3", 2014, null), "/m/Taken 3");

        Optional<String> original = guard.claim(movie("Taken 3", "Taken 3", 2014, null), "/m/Taken 3 (copy)");

        assertThat(original).contains("/m/Taken 3");
    }

    @Test
    void aSharedImdbIdCatchesTheSameFilmEvenWhenTitleAndYearDiffer() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.claim(movie("Heat", null, 1995, "tt0113277"), "/m/Heat");

        Optional<String> original = guard.claim(movie("Heat Redux", null, 1996, "tt0113277"), "/m/Heat alt");

        assertThat(original).contains("/m/Heat");
    }

    @Test
    void filmsWithoutAnImdbIdDoNotCollideOnItsAbsence() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.claim(movie("Alpha", null, 2000, null), "/m/Alpha");

        assertThat(guard.claim(movie("Beta", null, 2000, null), "/m/Beta")).isEmpty();
    }

    @Test
    void aRejectedDuplicateNeverClaimsItsOwnIdentitySoAThirdCopyStillPointsAtTheFirst() {
        DuplicateGuard guard = new DuplicateGuard();
        guard.claim(movie("Heat", null, 1995, "tt0113277"), "/m/Heat");
        guard.claim(movie("Heat", null, 1995, "tt0113277"), "/m/Heat (copy)");

        Optional<String> original = guard.claim(movie("Heat", null, 1995, "tt0113277"), "/m/Heat (copy 2)");

        assertThat(original).contains("/m/Heat");
    }
}
