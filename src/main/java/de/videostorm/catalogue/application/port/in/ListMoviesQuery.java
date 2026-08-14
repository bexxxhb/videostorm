package de.videostorm.catalogue.application.port.in;

import de.videostorm.catalogue.application.MoviePage;
import de.videostorm.catalogue.application.MovieSort;

/**
 * Inbound port for the movie listing. Page numbers are 1-based; {@code query} is the raw search box
 * input; {@code sort} is the active column and direction (never {@code null} — pass
 * {@link MovieSort#DEFAULT} for the default Title-ascending order).
 */
public interface ListMoviesQuery {

    MoviePage list(int pageNumber, String query, MovieSort sort);
}
