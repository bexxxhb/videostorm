package de.videostorm.indexing.domain;

/**
 * One thing a run found questionable, captured against the path it concerns. {@link #title} is the
 * entry's title where one is known (parsed or folder-derived) and {@link #field} carries the
 * type-specific detail: the thin field name for a {@link RunIssueType#MISSING_FIELD}, the location of
 * the already-catalogued film for a {@link RunIssueType#DUPLICATE} — otherwise both may be
 * {@code null}. Issue detail is attached to the run, not the catalogue, and is what the run report
 * later exports.
 */
public record RunIssue(RunIssueType type, String path, String title, String field) {

    /** The two fields the run report tracks as gaps: a derived title and an unknown year. */
    public static final String TITLE_FIELD = "title";
    public static final String YEAR_FIELD = "year";

    public RunIssue {
        if (type == null) {
            throw new IllegalArgumentException("Issue type must not be null");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Issue path must not be blank");
        }
    }

    /** A folder that held a metadata file but no video, so it produced no catalogue entry. */
    public static RunIssue noVideo(String path, String title) {
        return new RunIssue(RunIssueType.NO_VIDEO, path, title, null);
    }

    /** A video left out of the catalogue because a sibling was chosen as the folder's feature. */
    public static RunIssue ignoredVideo(String path, String title) {
        return new RunIssue(RunIssueType.IGNORED_VIDEO, path, title, null);
    }

    /** A catalogued entry that is thin in {@code field} — {@link #TITLE_FIELD} or {@link #YEAR_FIELD}. */
    public static RunIssue missingField(String path, String title, String field) {
        return new RunIssue(RunIssueType.MISSING_FIELD, path, title, field);
    }

    /**
     * A folder skipped because it resolved to a film already catalogued in this run, or an episode file
     * skipped because it resolved to a season and episode already claimed under its show. Both
     * locations are kept: {@code path} is the skipped entry, {@code originalPath} the one it duplicates.
     */
    public static RunIssue duplicate(String path, String title, String originalPath) {
        return new RunIssue(RunIssueType.DUPLICATE, path, title, originalPath);
    }

    /**
     * An episode file whose number could not be parsed from its filename, so it was left out of the
     * catalogue. {@code title} carries the show it sits under, for context in the report.
     */
    public static RunIssue skippedEpisode(String path, String title) {
        return new RunIssue(RunIssueType.SKIPPED_EPISODE, path, title, null);
    }
}
