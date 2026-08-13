package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.ShowSort;
import de.videostorm.catalogue.application.ShowSortField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The sort state shaped for the shows template: one {@link SortHeader} per sortable column (Title,
 * Started, Rating), plus the active field and direction as URL param strings for the search form's
 * hidden inputs, so submitting a search keeps the current sort. Shows have no resolution column.
 *
 * <p>Plain JavaBean rather than a record — Pug4j resolves model properties via {@code getXxx()}.
 */
@Getter
@RequiredArgsConstructor
public class ShowSortView {

    private final SortHeader title;
    private final SortHeader year;
    private final SortHeader rating;
    private final String field;
    private final String direction;

    static ShowSortView from(ShowSort sort, String query, String basePath) {
        return new ShowSortView(
                SortHeader.of(basePath, ShowSortField.TITLE.param(),
                        sort.field() == ShowSortField.TITLE, sort.direction(), query),
                SortHeader.of(basePath, ShowSortField.YEAR.param(),
                        sort.field() == ShowSortField.YEAR, sort.direction(), query),
                SortHeader.of(basePath, ShowSortField.RATING.param(),
                        sort.field() == ShowSortField.RATING, sort.direction(), query),
                sort.field().param(),
                sort.direction().param());
    }
}
