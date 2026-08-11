package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The run's per-show duplicate detection: two files under one show that resolve to the same season and
 * episode are caught as the show is walked, so the first in codepoint order is kept and the second is
 * skipped with both locations retained. The same numbers under different shows never collide.
 */
class EpisodeDuplicateGuardTest {

    @Test
    void theFirstFileToClaimASeasonAndEpisodeIsAdmitted() {
        EpisodeDuplicateGuard guard = new EpisodeDuplicateGuard();

        assertThat(guard.claim(ParsedEpisodeNumber.of(1, 1), "/s/S01E01.mkv")).isEmpty();
    }

    @Test
    void aSecondFileWithTheSameSeasonAndEpisodeIsRejectedWithTheFirstPath() {
        EpisodeDuplicateGuard guard = new EpisodeDuplicateGuard();
        guard.claim(ParsedEpisodeNumber.of(1, 1), "/s/S01E01.mkv");

        Optional<String> original = guard.claim(ParsedEpisodeNumber.of(1, 1), "/s/1.01.mkv");

        assertThat(original).contains("/s/S01E01.mkv");
    }

    @Test
    void aDifferentEpisodeInTheSameSeasonDoesNotCollide() {
        EpisodeDuplicateGuard guard = new EpisodeDuplicateGuard();
        guard.claim(ParsedEpisodeNumber.of(1, 1), "/s/S01E01.mkv");

        assertThat(guard.claim(ParsedEpisodeNumber.of(1, 2), "/s/S01E02.mkv")).isEmpty();
    }

    @Test
    void theSameEpisodeNumberInADifferentSeasonDoesNotCollide() {
        EpisodeDuplicateGuard guard = new EpisodeDuplicateGuard();
        guard.claim(ParsedEpisodeNumber.of(1, 1), "/s/S01E01.mkv");

        assertThat(guard.claim(ParsedEpisodeNumber.of(2, 1), "/s/S02E01.mkv")).isEmpty();
    }

    @Test
    void aRejectedDuplicateNeverReplacesTheFirstSoAThirdCopyStillPointsAtTheFirst() {
        EpisodeDuplicateGuard guard = new EpisodeDuplicateGuard();
        guard.claim(ParsedEpisodeNumber.of(1, 1), "/s/first.mkv");
        guard.claim(ParsedEpisodeNumber.of(1, 1), "/s/second.mkv");

        Optional<String> original = guard.claim(ParsedEpisodeNumber.of(1, 1), "/s/third.mkv");

        assertThat(original).contains("/s/first.mkv");
    }
}
