package de.videostorm.catalogue.application.port.out;

import de.videostorm.catalogue.domain.SearchTerm;
import de.videostorm.catalogue.domain.Show;

import java.util.List;

/**
 * Outbound port for reading the live show table. The read side never touches the filesystem —
 * this is the only way it reaches the catalogue.
 */
public interface ShowRepository {

    /** Shows matching {@code searchTerm}, or every show when it's blank. */
    long count(SearchTerm searchTerm);

    /**
     * Returns one page of shows matching {@code searchTerm} (every show when it's blank),
     * fixed-sorted by normalized title, then year, then id. {@code pageNumber} is 1-based.
     */
    List<Show> findPage(SearchTerm searchTerm, int pageNumber, int pageSize);
}
