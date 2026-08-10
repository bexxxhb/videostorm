package de.videostorm.indexing.domain;

import java.math.BigDecimal;

/**
 * One {@code <rating>} read from an Emby {@code .nfo}: the provider that scored the film, its value
 * on that provider's scale, the scale's maximum and how many votes it rests on. Any field may be
 * {@code null} when the file omits it or writes something unparseable. {@link #isDefault()} marks
 * the provider Emby flagged {@code default="true"} — the one the listing shows.
 */
public record ParsedRating(
        String source,
        BigDecimal value,
        BigDecimal max,
        Integer votes,
        boolean isDefault) {
}
