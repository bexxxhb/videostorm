package de.videostorm.catalogue.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The single normalization rule shared by the sort key, the identity key and (later) search
 * input, so they can never drift apart. Lowercases, strips diacritics via NFD decomposition,
 * collapses runs of non-alphanumeric characters to a single space, and trims — producing plain
 * ASCII that sorts identically regardless of the database's collation.
 */
public final class TitleNormalizer {

    private TitleNormalizer() {
    }

    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String lowercased = title.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lowercased, Normalizer.Form.NFD);
        String withoutDiacritics = decomposed.replaceAll("\\p{Mn}", "");
        String collapsed = withoutDiacritics.replaceAll("[^a-z0-9]+", " ");
        return collapsed.trim();
    }
}
