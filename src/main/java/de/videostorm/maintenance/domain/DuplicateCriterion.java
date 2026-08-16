package de.videostorm.maintenance.domain;

/**
 * The attribute two movies share to be considered duplicates. The scan runs each criterion
 * independently and unions the results, so one movie may fall into a group under either — or both.
 */
public enum DuplicateCriterion {

    /** Exact match on the IMDb id. */
    IMDB_ID("IMDb ID"),

    /** Match on the original title after lowercasing and trimming surrounding whitespace. */
    ORIGINAL_TITLE("Original title");

    private final String label;

    DuplicateCriterion(String label) {
        this.label = label;
    }

    /** Human-readable name shown in the drill-down layer. */
    public String label() {
        return label;
    }
}
