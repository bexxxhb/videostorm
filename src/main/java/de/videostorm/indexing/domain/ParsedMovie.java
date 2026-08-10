package de.videostorm.indexing.domain;

import java.util.List;

/**
 * The Emby movie fields lifted out of one {@code .nfo}, before any catalogue-shaped derivation
 * (normalisation, slug, storage encoding) is applied. Absent fields are {@code null} — or an empty
 * list for the repeated ones — so a thinly scraped file still parses; one unreadable field costs
 * only that field, never the whole entry.
 */
public record ParsedMovie(
        String title,
        String originalTitle,
        Integer year,
        List<ParsedRating> ratings,
        List<String> genres,
        Integer runtimeMinutes,
        String plot,
        String setName,
        String collectionId,
        String imdbId,
        String tvdbId,
        String tmdbId) {

    public ParsedMovie {
        ratings = ratings == null ? List.of() : List.copyOf(ratings);
        genres = genres == null ? List.of() : List.copyOf(genres);
    }
}
