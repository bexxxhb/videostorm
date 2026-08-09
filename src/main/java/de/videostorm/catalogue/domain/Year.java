package de.videostorm.catalogue.domain;

/**
 * A movie's release year, or the explicit absence of one. {@code 0} is the unknown state — it
 * comes only from the {@code .nfo}, never from a filename, and renders as a blank cell.
 */
public record Year(int value) {

    public static final Year UNKNOWN = new Year(0);

    public static Year of(int value) {
        return value <= 0 ? UNKNOWN : new Year(value);
    }

    public boolean isKnown() {
        return value > 0;
    }
}
