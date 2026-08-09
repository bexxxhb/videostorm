package de.videostorm.catalogue.domain;

import java.util.Locale;

/** Mapped from the {@code .nfo}'s {@code <status>} element; absent or unrecognised becomes {@code UNKNOWN}. */
public enum ShowStatus {
    ENDED,
    CONTINUING,
    UNKNOWN;

    public String displayLabel() {
        return name().toLowerCase(Locale.ROOT);
    }
}
