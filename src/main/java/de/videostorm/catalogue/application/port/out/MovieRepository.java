package de.videostorm.catalogue.application.port.out;

import de.videostorm.catalogue.application.MovieSort;
import de.videostorm.catalogue.domain.Movie;
import de.videostorm.catalogue.domain.SearchTerm;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for reading the live movie table. The read side never touches the filesystem —
 * this is the only way it reaches the catalogue.
 */
public interface MovieRepository {

    /** Movies matching {@code searchTerm}, or every movie when it's blank. */
    long count(SearchTerm searchTerm);

    /**
     * Returns one page of movies matching {@code searchTerm} (every movie when it's blank), sorted by
     * {@code sort} with entries that have no value for that column pushed to the end (in both
     * directions) and {@code id} as a deterministic tiebreak. {@code pageNumber} is 1-based.
     */
    List<Movie> findPage(SearchTerm searchTerm, MovieSort sort, int pageNumber, int pageSize);

    /**
     * The raw {@code .nfo} text for the movie with {@code id}, fetched on demand. Empty when there is
     * no such movie or its {@code raw_nfo} is null.
     */
    Optional<String> findRawNfo(long id);
}
