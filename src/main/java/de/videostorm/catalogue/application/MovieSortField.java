package de.videostorm.catalogue.application;

/**
 * The columns a movie listing may be sorted by — the whitelist an incoming {@code sort} parameter is
 * mapped through, so a client string never reaches {@code Sort}/SQL directly. {@link #TITLE} is the
 * default and the fallback for anything unrecognised. Every other movie column (genres, runtime,
 * plot, IMDb) is deliberately absent, so it is not sortable.
 */
public enum MovieSortField {

    TITLE("title"),
    YEAR("year"),
    RATING("rating"),
    RESOLUTION("resolution");

    private final String param;

    MovieSortField(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    /** The field named by {@code param} (case-insensitive), or {@link #TITLE} for anything else. */
    public static MovieSortField fromParam(String param) {
        if (param != null) {
            String trimmed = param.trim();
            for (MovieSortField field : values()) {
                if (field.param.equalsIgnoreCase(trimmed)) {
                    return field;
                }
            }
        }
        return TITLE;
    }
}
