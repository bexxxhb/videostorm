package de.videostorm.catalogue.application;

/**
 * The columns a show listing may be sorted by — the whitelist an incoming {@code sort} parameter is
 * mapped through, so a client string never reaches {@code Sort}/SQL directly. {@link #TITLE} is the
 * default and the fallback for anything unrecognised. Shows have no resolution column, so unlike
 * {@link MovieSortField} there is no {@code RESOLUTION} here.
 */
public enum ShowSortField {

    TITLE("title"),
    YEAR("year"),
    RATING("rating");

    private final String param;

    ShowSortField(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    /** The field named by {@code param} (case-insensitive), or {@link #TITLE} for anything else. */
    public static ShowSortField fromParam(String param) {
        if (param != null) {
            String trimmed = param.trim();
            for (ShowSortField field : values()) {
                if (field.param.equalsIgnoreCase(trimmed)) {
                    return field;
                }
            }
        }
        return TITLE;
    }
}
