package de.videostorm.catalogue.application.port.out;

import de.videostorm.catalogue.domain.Movie;

import java.util.List;

/**
 * Outbound port for reading the live movie table. The read side never touches the filesystem —
 * this is the only way it reaches the catalogue.
 */
public interface MovieRepository {

    long count();

    /** Returns one page, fixed-sorted by normalized title, then year, then id. {@code pageNumber} is 1-based. */
    List<Movie> findPage(int pageNumber, int pageSize);
}
