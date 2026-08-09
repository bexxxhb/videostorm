package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.domain.Movie;
import de.videostorm.catalogue.domain.Rating;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The template is kept dumb: every string here is already display-ready, including the zebra
 * row class, so Pug never has to evaluate arithmetic or ternaries.
 *
 * <p>Plain JavaBean rather than a record — Pug4j resolves model properties via {@code getXxx()},
 * which record accessors ({@code xxx()}) don't satisfy.
 */
@Getter
@RequiredArgsConstructor
public class MovieRow {

    private static final String EVEN_ROW_CLASS = "row-even";
    private static final String ODD_ROW_CLASS = "row-odd";

    private final String rowClass;
    private final String title;
    private final String year;
    private final String ratingDisplay;
    private final String genresDisplay;
    private final String genresFullText;
    private final String runtimeDisplay;

    static MovieRow from(Movie movie, int index) {
        return new MovieRow(
                index % 2 == 0 ? EVEN_ROW_CLASS : ODD_ROW_CLASS,
                movie.title(),
                movie.year().isKnown() ? String.valueOf(movie.year().value()) : "",
                movie.rating().map(Rating::displayLabel).orElse(""),
                movie.genres().displayLabel(),
                movie.genres().fullText(),
                movie.runtimeMinutes().map(String::valueOf).orElse(""));
    }
}
