package de.videostorm.catalogue.application;

/**
 * The active sort of a show listing: one whitelisted {@link ShowSortField} and a
 * {@link SortDirection}. Built from the raw request parameters via {@link #fromParams(String, String)},
 * which falls back to {@link #DEFAULT} (Title ascending) for anything unrecognised, so an invalid or
 * missing {@code sort}/{@code dir} can never fail the request or reach SQL.
 */
public record ShowSort(ShowSortField field, SortDirection direction) {

    public static final ShowSort DEFAULT = new ShowSort(ShowSortField.TITLE, SortDirection.ASC);

    public ShowSort {
        if (field == null || direction == null) {
            throw new IllegalArgumentException("A show sort needs a field and a direction");
        }
    }

    public static ShowSort fromParams(String sort, String dir) {
        return new ShowSort(ShowSortField.fromParam(sort), SortDirection.fromParam(dir));
    }
}
