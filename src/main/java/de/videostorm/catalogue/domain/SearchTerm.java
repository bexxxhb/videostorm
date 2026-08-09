package de.videostorm.catalogue.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One search box's raw input, trimmed once so every column it can match against — normalized
 * title, normalized original title, genres, year — derives from the same trimmed value. The
 * input is never tokenized: it is always matched as a single literal string.
 */
public record SearchTerm(String raw) {

    private static final Pattern FOUR_DIGITS = Pattern.compile("\\d{4}");
    private static final String GENRE_DELIMITER = "|";

    public SearchTerm {
        raw = raw == null ? "" : raw.trim();
    }

    public boolean isBlank() {
        return raw.isEmpty();
    }

    /** The title-matching key — the same normalization as the sort key, so search can never drift from it. */
    public String normalizedTitle() {
        return TitleNormalizer.normalize(raw);
    }

    /** The genre-matching fragment, with the storage delimiter stripped so a term can never match across two genres. */
    public String genreFragment() {
        return raw.replace(GENRE_DELIMITER, "");
    }

    /** The exact year to match, present only when the whole term is four digits. */
    public Optional<Integer> yearExactMatch() {
        return FOUR_DIGITS.matcher(raw).matches() ? Optional.of(Integer.valueOf(raw)) : Optional.empty();
    }
}
