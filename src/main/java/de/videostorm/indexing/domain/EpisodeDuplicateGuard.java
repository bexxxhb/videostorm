package de.videostorm.indexing.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Catches two episode files under one show that resolve to the same season and episode as the show's
 * files are walked, so the second is skipped rather than staged twice — the run's own enforcement of
 * the {@code (show, season, episode)} uniqueness, with the unique constraint on the tables as the
 * schema backstop. The first file claimed for a given season and episode is the one kept; a later file
 * with the same numbers is rejected, and the caller records it against both locations.
 *
 * <p>One guard is used per show and holds only what that show has seen, so numbers repeat freely
 * across different shows. Files must be offered in the deterministic codepoint order the scan walks
 * them in, so "the first kept" is a stable, repeatable choice.
 */
public final class EpisodeDuplicateGuard {

    // Season and episode are both non-negative integers, so a pipe — never a digit — keeps two
    // distinct pairs such as (1, 23) and (12, 3) from ever forming the same key. A String, not a
    // char, so the surrounding int operands concatenate rather than sum.
    private static final String KEY_SEPARATOR = "|";

    private final Map<String, String> byNumber = new HashMap<>();

    /**
     * Registers {@code parsed} as claimed at {@code path}. If an earlier file already claimed the same
     * season and episode, returns that file's path and records nothing — the caller skips this file.
     * Otherwise remembers it and returns empty.
     */
    public Optional<String> claim(ParsedEpisodeNumber parsed, String path) {
        String key = parsed.season().value() + KEY_SEPARATOR + parsed.episode();
        String original = byNumber.putIfAbsent(key, path);
        return Optional.ofNullable(original);
    }
}
