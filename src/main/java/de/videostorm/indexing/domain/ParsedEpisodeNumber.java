package de.videostorm.indexing.domain;

/**
 * The season and episode {@link EpisodeNumberParser} lifted out of one episode filename. An absent
 * result is expressed as an empty {@link java.util.Optional} by the parser, not by a sentinel here, so
 * every {@code ParsedEpisodeNumber} that exists carries a real, matched number.
 */
public record ParsedEpisodeNumber(SeasonNumber season, int episode) {

    public ParsedEpisodeNumber {
        if (season == null) {
            throw new IllegalArgumentException("Season must not be null");
        }
    }

    public static ParsedEpisodeNumber of(int season, int episode) {
        return new ParsedEpisodeNumber(new SeasonNumber(season), episode);
    }
}
