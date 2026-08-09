package de.videostorm.catalogue.domain;

import java.math.BigDecimal;

/**
 * Emby writes only the scraping provider's rating (TMDB for movies, TVDB for shows), never an
 * IMDb score, so the display is generic: the value together with whichever provider produced it.
 */
public record Rating(String source, BigDecimal value) {

    public String displayLabel() {
        return value.toPlainString() + " (" + source + ")";
    }
}
