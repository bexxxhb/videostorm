package de.videostorm.catalogue.application.port.out;

import de.videostorm.catalogue.application.ShowSort;
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
     * Returns one page of shows matching {@code searchTerm} (every show when it's blank), sorted by
     * {@code sort} with entries that have no value for that column pushed to the end (in both
     * directions) and {@code id} as a deterministic tiebreak. {@code pageNumber} is 1-based.
     */
    List<Show> findPage(SearchTerm searchTerm, ShowSort sort, int pageNumber, int pageSize);
}
