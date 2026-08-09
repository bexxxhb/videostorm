package de.videostorm.catalogue.application;

import de.videostorm.catalogue.domain.Show;

import java.util.List;

/** {@code query} is the trimmed, raw search box input that produced this page — echoed back into the search box and pagination links. */
public record ShowPage(List<Show> shows, int pageNumber, int totalPages, long totalElements, String query) {

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean hasNext() {
        return pageNumber < totalPages;
    }
}
