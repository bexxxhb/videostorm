package de.videostorm.catalogue.adapter.out.persistence;

import de.videostorm.catalogue.application.SortDirection;
import org.springframework.data.domain.Sort;

/**
 * Builds the {@code Sort} a listing page pages by: the chosen column in the requested direction, with
 * "no value" (a null sort key) pushed to the end in either direction, then {@code id} as a
 * deterministic tiebreak so paging can never duplicate or skip a row. Shared by the movie and show
 * repository adapters, which differ only in mapping their own sort field to a column property.
 */
final class ListingSort {

    private ListingSort() {
    }

    static Sort by(String property, SortDirection direction) {
        Sort.Order order = direction == SortDirection.DESC ? Sort.Order.desc(property) : Sort.Order.asc(property);
        return Sort.by(order.nullsLast(), Sort.Order.asc("id"));
    }
}
