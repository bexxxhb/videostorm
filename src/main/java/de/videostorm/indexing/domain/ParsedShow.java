package de.videostorm.indexing.domain;

import java.util.List;

/**
 * The Emby show fields lifted out of one {@code .nfo}, before any catalogue-shaped derivation
 * (normalisation, slug, year from the premiered date, status mapping) is applied. Absent fields are
 * {@code null} — or an empty list for the repeated ones — so a thinly scraped file still parses; one
 * unreadable field costs only that field, never the whole entry.
 *
 * <p>The year is not carried here as an integer: a show's year comes from {@link #premiered}, the raw
 * date string, which {@link StagedShow} turns into a year via {@link PremieredYear}.
 */
public record ParsedShow(
        String title,
        String originalTitle,
        String premiered,
        List<ParsedRating> ratings,
        List<String> genres,
        String plot,
        String status,
        String imdbId,
        String tvdbId,
        String tmdbId) {

    public ParsedShow {
        ratings = ratings == null ? List.of() : List.copyOf(ratings);
        genres = genres == null ? List.of() : List.copyOf(genres);
    }

    /**
     * A show about which nothing is known: the state a folder with no metadata file, or one whose
     * {@code .nfo} was too broken to read, falls back to. Everything is absent, so the derivations
     * downstream supply a folder-derived title, a zero year and an unknown status.
     */
    public static ParsedShow absent() {
        return new ParsedShow(null, null, null, null, null, null, null, null, null, null);
    }
}
