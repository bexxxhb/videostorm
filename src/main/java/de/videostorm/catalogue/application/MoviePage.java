package de.videostorm.catalogue.application;

import de.videostorm.catalogue.domain.Movie;

import java.util.List;

/**
 * {@code query} is the trimmed, raw search box input that produced this page — echoed back into the
 * search box and pagination links. {@code sort} is the active column and direction, carried so the
 * headers, pagination links and search form can preserve it.
 */
public record MoviePage(List<Movie> movies, int pageNumber, int totalPages, long totalElements,
                        String query, MovieSort sort) {

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean hasNext() {
        return pageNumber < totalPages;
    }
}
