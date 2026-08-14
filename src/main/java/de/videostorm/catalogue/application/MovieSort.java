package de.videostorm.catalogue.application;

/**
 * The active sort of a movie listing: one whitelisted {@link MovieSortField} and a
 * {@link SortDirection}. Built from the raw request parameters via {@link #fromParams(String, String)},
 * which falls back to {@link #DEFAULT} (Title ascending) for anything unrecognised, so an invalid or
 * missing {@code sort}/{@code dir} can never fail the request or reach SQL.
 */
public record MovieSort(MovieSortField field, SortDirection direction) {

    public static final MovieSort DEFAULT = new MovieSort(MovieSortField.TITLE, SortDirection.ASC);

    public MovieSort {
        if (field == null || direction == null) {
            throw new IllegalArgumentException("A movie sort needs a field and a direction");
        }
    }

    public static MovieSort fromParams(String sort, String dir) {
        return new MovieSort(MovieSortField.fromParam(sort), SortDirection.fromParam(dir));
    }
}
