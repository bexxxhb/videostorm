package de.videostorm.indexing.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Catches the same film appearing in two folders as a single run walks the library, so the second is
 * skipped rather than staged twice — the run's own enforcement of the catalogue's identity, with the
 * unique indexes on the tables as the schema backstop. Two films count as the same when they share a
 * {@link StagedMovie#normalizedIdentityTitle() normalised identity title} and year, or share an imdb
 * id; the normalisation means differences of punctuation, capitalisation or diacritics never make one
 * film read as two. Films differing in year, or with no imdb id, do not collide on that alone.
 *
 * <p>One guard is used per run and holds only what that run has seen, so a re-index starts from a
 * clean slate. A rejected duplicate is not remembered under its own path, so every copy of a film
 * points back at the one location that was actually catalogued.
 */
public final class DuplicateGuard {

    // A normalised title is only [a-z0-9 ], so a pipe (outside that set) keeps two distinct
    // (title, year) pairs such as "the thing"/2011 and "the thing 2011"/0 from forming one key.
    private static final char KEY_SEPARATOR = '|';

    private final Map<String, String> byTitleAndYear = new HashMap<>();
    private final Map<String, String> byImdbId = new HashMap<>();

    /**
     * Registers {@code movie} as catalogued at {@code path}. If another folder already claimed the
     * same identity, returns that folder's path and records nothing — the caller skips this movie.
     * Otherwise remembers it and returns empty.
     */
    public Optional<String> claim(StagedMovie movie, String path) {
        String titleAndYear = movie.normalizedIdentityTitle() + KEY_SEPARATOR + movie.year();
        String imdbId = hasText(movie.imdbId()) ? movie.imdbId() : null;

        String original = byTitleAndYear.get(titleAndYear);
        if (original == null && imdbId != null) {
            original = byImdbId.get(imdbId);
        }
        if (original != null) {
            return Optional.of(original);
        }

        byTitleAndYear.put(titleAndYear, path);
        if (imdbId != null) {
            byImdbId.put(imdbId, path);
        }
        return Optional.empty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
