package de.videostorm.catalogue.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Genres in Emby's stored order. There is no genre lookup table: the persisted form is a single
 * delimiter-padded column ({@code |Action|Thriller|}), parsed here rather than rendered raw.
 */
public record GenreList(List<String> values) {

    public static final GenreList EMPTY = new GenreList(List.of());

    private static final String DELIMITER = "|";
    private static final int DISPLAY_LIMIT = 3;

    public GenreList {
        values = List.copyOf(values);
    }

    public static GenreList parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return EMPTY;
        }
        List<String> values = Arrays.stream(stored.split("\\|"))
                .filter(value -> !value.isBlank())
                .toList();
        return new GenreList(values);
    }

    public String toStorage() {
        return values.isEmpty() ? null : DELIMITER + String.join(DELIMITER, values) + DELIMITER;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public String displayLabel() {
        if (values.size() <= DISPLAY_LIMIT) {
            return String.join(", ", values);
        }
        int hidden = values.size() - DISPLAY_LIMIT;
        return String.join(", ", values.subList(0, DISPLAY_LIMIT)) + " +" + hidden;
    }

    public String fullText() {
        return String.join(", ", values);
    }
}
