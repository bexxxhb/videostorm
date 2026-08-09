package de.videostorm.catalogue.application;

import de.videostorm.catalogue.domain.Movie;

import java.util.List;

public record MoviePage(List<Movie> movies, int pageNumber, int totalPages, long totalElements) {

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean hasNext() {
        return pageNumber < totalPages;
    }
}
