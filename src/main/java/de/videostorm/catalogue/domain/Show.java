package de.videostorm.catalogue.domain;

import java.util.Optional;

/** The subset of the show aggregate the catalogue listing reads and displays. */
public record Show(
        long id,
        String title,
        Year year,
        ShowStatus status,
        Optional<Rating> rating,
        GenreList genres,
        Optional<String> plot,
        int seasonCount,
        int episodeCount,
        Optional<String> imdbId) {
}
