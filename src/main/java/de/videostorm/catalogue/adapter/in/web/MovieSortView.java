package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.MovieSort;
import de.videostorm.catalogue.application.MovieSortField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The sort state shaped for the movies template: one {@link SortHeader} per sortable column (Title,
 * Year, Rating, Resolution), plus the active field and direction as URL param strings for the search
 * form's hidden inputs, so submitting a search keeps the current sort.
 *
 * <p>Plain JavaBean rather than a record — Pug4j resolves model properties via {@code getXxx()}.
 */
@Getter
@RequiredArgsConstructor
public class MovieSortView {

    private final SortHeader title;
    private final SortHeader year;
    private final SortHeader rating;
    private final SortHeader resolution;
    private final String field;
    private final String direction;

    static MovieSortView from(MovieSort sort, String query, String basePath) {
        return new MovieSortView(
                SortHeader.of(basePath, MovieSortField.TITLE.param(),
                        sort.field() == MovieSortField.TITLE, sort.direction(), query),
                SortHeader.of(basePath, MovieSortField.YEAR.param(),
                        sort.field() == MovieSortField.YEAR, sort.direction(), query),
                SortHeader.of(basePath, MovieSortField.RATING.param(),
                        sort.field() == MovieSortField.RATING, sort.direction(), query),
                SortHeader.of(basePath, MovieSortField.RESOLUTION.param(),
                        sort.field() == MovieSortField.RESOLUTION, sort.direction(), query),
                sort.field().param(),
                sort.direction().param());
    }
}
