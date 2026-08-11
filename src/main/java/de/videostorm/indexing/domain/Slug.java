package de.videostorm.indexing.domain;

import de.videostorm.catalogue.domain.TitleNormalizer;

/**
 * Derives an entry's identity slug from its title and year — the same shape for a movie or a show.
 * The title is run through the shared {@link TitleNormalizer} — lowercased, accents stripped,
 * punctuation collapsed — then spaces become hyphens and the year is appended. A year of {@code 0}
 * (unknown) is kept as {@code -0} rather than omitted, so the slug still reads as a complete identity.
 */
public final class Slug {

    private Slug() {
    }

    public static String of(String title, int year) {
        String normalized = TitleNormalizer.normalize(title).replace(' ', '-');
        return normalized + "-" + year;
    }
}
