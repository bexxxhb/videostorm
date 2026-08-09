package de.videostorm.catalogue.application.port.out;

import de.videostorm.catalogue.domain.Movie;
import de.videostorm.catalogue.domain.SearchTerm;

import java.util.List;

/**
 * Outbound port for reading the live movie table. The read side never touches the filesystem —
 * this is the only way it reaches the catalogue.
 */
public interface MovieRepository {

    /** Movies matching {@code searchTerm}, or every movie when it's blank. */
    long count(SearchTerm searchTerm);

    /**
     * Returns one page of movies matching {@code searchTerm} (every movie when it's blank),
     * fixed-sorted by normalized title, then year, then id. {@code pageNumber} is 1-based.
     */
    List<Movie> findPage(SearchTerm searchTerm, int pageNumber, int pageSize);
}
