package de.videostorm.indexing.domain;

/**
 * Derives a show's year from its Emby {@code <premiered>} date. Emby writes an ISO date
 * ({@code 2008-01-20}); the year is the leading four digits. An absent, blank or unparseable value
 * yields {@code 0}, the unknown state — the year is taken only from the metadata, never guessed from
 * a folder name, exactly as a movie's year is.
 */
public final class PremieredYear {

    private static final int YEAR_LENGTH = 4;

    private PremieredYear() {
    }

    public static int from(String premiered) {
        if (premiered == null) {
            return 0;
        }
        String trimmed = premiered.trim();
        if (trimmed.length() < YEAR_LENGTH) {
            return 0;
        }
        String head = trimmed.substring(0, YEAR_LENGTH);
        for (int i = 0; i < YEAR_LENGTH; i++) {
            if (!Character.isDigit(head.charAt(i))) {
                return 0;
            }
        }
        return Integer.parseInt(head);
    }
}
