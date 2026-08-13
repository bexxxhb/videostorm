package de.videostorm.catalogue.application;

/**
 * The direction a listing column is sorted in. The {@code param} is the URL value carried in the
 * {@code dir} query parameter; anything unrecognised falls back to {@link #ASC}, the default.
 */
public enum SortDirection {

    ASC("asc"),
    DESC("desc");

    private final String param;

    SortDirection(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    /** The opposite direction — used to build a header link that toggles the active column. */
    public SortDirection opposite() {
        return this == ASC ? DESC : ASC;
    }

    /** The direction named by {@code param} (case-insensitive), or {@link #ASC} for anything else. */
    public static SortDirection fromParam(String param) {
        if (param != null && DESC.param.equalsIgnoreCase(param.trim())) {
            return DESC;
        }
        return ASC;
    }
}
