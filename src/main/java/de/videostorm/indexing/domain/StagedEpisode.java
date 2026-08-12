package de.videostorm.indexing.domain;

/**
 * An episode ready to be written to the staging tables: just the season and episode numbers parsed
 * from its filename, to be stored against the show that owns it. There is no season table — the
 * {@code seasonNumber} lives on the episode row — and episodes carry nothing else in this scope, since
 * they are stored but displayed nowhere. The owning show's id is supplied at write time rather than
 * held here, so a {@code StagedEpisode} is a pure value with no identity of its own.
 */
public record StagedEpisode(int seasonNumber, int episodeNumber) {

    public static StagedEpisode from(ParsedEpisodeNumber parsed) {
        return new StagedEpisode(parsed.season().value(), parsed.episode());
    }
}
