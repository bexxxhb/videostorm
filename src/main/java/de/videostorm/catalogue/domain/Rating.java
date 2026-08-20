package de.videostorm.catalogue.domain;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Emby writes only the scraping provider's rating (TMDB for movies, TVDB for shows), never an
 * IMDb score. {@code source} is kept for internal use but not shown; the display favours the vote
 * count behind the score, falling back to the bare value when the source .nfo carried no votes.
 */
public record Rating(String source, BigDecimal value, Integer votes) {

    public String displayLabel() {
        if (votes == null) {
            return value.toPlainString();
        }
        return value.toPlainString() + " (" + String.format(Locale.GERMANY, "%,d", votes) + " votes)";
    }
}
