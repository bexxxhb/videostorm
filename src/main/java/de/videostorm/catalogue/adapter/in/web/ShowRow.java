package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.domain.Rating;
import de.videostorm.catalogue.domain.Show;
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
public class ShowRow {

    private static final String EVEN_ROW_CLASS = "row-even";
    private static final String ODD_ROW_CLASS = "row-odd";

    private final String rowClass;
    private final String title;
    private final String year;
    private final String statusDisplay;
    private final String ratingDisplay;
    private final String genresDisplay;
    private final String genresFullText;

    static ShowRow from(Show show, int index) {
        return new ShowRow(
                index % 2 == 0 ? EVEN_ROW_CLASS : ODD_ROW_CLASS,
                show.title(),
                show.year().isKnown() ? String.valueOf(show.year().value()) : "",
                show.status().displayLabel(),
                show.rating().map(Rating::displayLabel).orElse(""),
                show.genres().displayLabel(),
                show.genres().fullText());
    }
}
