package de.videostorm.indexing.domain;

/**
 * The kinds of thing a run records as questionable, so nothing disappears silently. Each is attached
 * to the run that found it, never to the catalogue, so a re-index reports afresh without polluting the
 * films it produced.
 *
 * <ul>
 *   <li>{@link #MISSING_FIELD} — a catalogued entry is thin in one field the report tracks: its title
 *       had to be derived from the folder name, or its year is unknown. The field is named on the
 *       issue.</li>
 *   <li>{@link #NO_VIDEO} — a folder held a metadata file but no video, so it produced no entry.</li>
 *   <li>{@link #IGNORED_VIDEO} — a folder held several videos; this one was not chosen as the feature
 *       and was left out of the catalogue.</li>
 *   <li>{@link #DUPLICATE} — a folder resolved to a film already catalogued in this run, or an episode
 *       file resolved to a season and episode already claimed under its show, so it was skipped; the
 *       location it duplicates is named on the issue.</li>
 *   <li>{@link #SKIPPED_EPISODE} — an episode file whose season and episode number could not be parsed
 *       from its filename, so it was left out of the catalogue rather than stored under a guess.</li>
 * </ul>
 */
public enum RunIssueType {
    MISSING_FIELD,
    NO_VIDEO,
    IGNORED_VIDEO,
    DUPLICATE,
    SKIPPED_EPISODE
}
