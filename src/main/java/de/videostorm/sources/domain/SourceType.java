package de.videostorm.sources.domain;

/**
 * The two kinds of media the catalogue indexes, each fed by its own set of source paths. The
 * label is the singular noun used in operator-facing messages ("movie source path", "show
 * source path").
 */
public enum SourceType {

    MOVIES("movie"),
    SHOWS("show");

    private final String label;

    SourceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The capitalised plural used as a display heading and in the run report ("Movies", "Shows"). */
    public String plural() {
        return this == MOVIES ? "Movies" : "Shows";
    }
}
