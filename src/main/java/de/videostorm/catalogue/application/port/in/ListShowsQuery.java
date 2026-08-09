package de.videostorm.catalogue.application.port.in;

import de.videostorm.catalogue.application.ShowPage;

/** Inbound port for the show listing. Page numbers are 1-based; {@code query} is the raw search box input. */
public interface ListShowsQuery {

    ShowPage list(int pageNumber, String query);
}
