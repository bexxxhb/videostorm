package de.videostorm.catalogue.application.port.in;

import de.videostorm.catalogue.application.ShowPage;
import de.videostorm.catalogue.application.ShowSort;

/**
 * Inbound port for the show listing. Page numbers are 1-based; {@code query} is the raw search box
 * input; {@code sort} is the active column and direction (never {@code null} — pass
 * {@link ShowSort#DEFAULT} for the default Title-ascending order).
 */
public interface ListShowsQuery {

    ShowPage list(int pageNumber, String query, ShowSort sort);
}
