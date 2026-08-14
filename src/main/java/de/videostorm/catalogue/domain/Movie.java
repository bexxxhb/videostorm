package de.videostorm.catalogue.domain;

import java.util.Optional;

/** The subset of the movie aggregate the catalogue listing reads and displays. */
public record Movie(
        long id,
        String title,
        Year year,
        Optional<Rating> rating,
        GenreList genres,
        Optional<Integer> runtimeMinutes,
        Optional<String> resolution,
        Optional<String> imdbId,
        Optional<String> plot,
        boolean hasRawNfo) {
}
