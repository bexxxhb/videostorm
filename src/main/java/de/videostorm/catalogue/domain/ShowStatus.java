package de.videostorm.catalogue.domain;

import java.util.Locale;

/** Mapped from the {@code .nfo}'s {@code <status>} element; absent or unrecognised becomes {@code UNKNOWN}. */
public enum ShowStatus {
    ENDED,
    CONTINUING,
    UNKNOWN;

    /**
     * Maps an Emby {@code <status>} value onto the catalogue's three states, case-insensitively and
     * ignoring surrounding whitespace. Anything absent, blank or unrecognised is {@link #UNKNOWN}, so
     * a thinly scraped or novel status never fails the entry.
     */
    public static ShowStatus fromNfo(String status) {
        if (status == null) {
            return UNKNOWN;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "ended" -> ENDED;
            case "continuing" -> CONTINUING;
            default -> UNKNOWN;
        };
    }

    public String displayLabel() {
        return name().toLowerCase(Locale.ROOT);
    }
}
