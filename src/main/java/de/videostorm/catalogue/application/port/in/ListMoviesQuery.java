package de.videostorm.catalogue.application.port.in;

import de.videostorm.catalogue.application.MoviePage;

/** Inbound port for the movie listing. Page numbers are 1-based. */
public interface ListMoviesQuery {

    MoviePage list(int pageNumber);
}
