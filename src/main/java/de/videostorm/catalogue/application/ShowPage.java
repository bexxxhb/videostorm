package de.videostorm.catalogue.application;

import de.videostorm.catalogue.domain.Show;

import java.util.List;

/**
 * {@code query} is the trimmed, raw search box input that produced this page — echoed back into the
 * search box and pagination links. {@code sort} is the active column and direction, carried so the
 * headers, pagination links and search form can preserve it.
 */
public record ShowPage(List<Show> shows, int pageNumber, int totalPages, long totalElements,
                       String query, ShowSort sort, int pageSize) {

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean hasNext() {
        return pageNumber < totalPages;
    }

    /**
     * 1-based position of this page's first row within the full result set, so the listing can render
     * a running index that continues across pages (page 2 starts at {@code pageSize + 1}).
     */
    public long firstItemNumber() {
        return (long) (pageNumber - 1) * pageSize + 1;
    }
}
