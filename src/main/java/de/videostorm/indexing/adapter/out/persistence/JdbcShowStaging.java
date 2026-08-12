package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.indexing.application.port.out.ShowStaging;
import de.videostorm.indexing.domain.ParsedRating;
import de.videostorm.indexing.domain.StagedEpisode;
import de.videostorm.indexing.domain.StagedShow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes show staging with {@link NamedParameterJdbcTemplate}: the importer never goes through JPA,
 * which reads the live tables only. Each {@link #stage} is its own transaction ({@code REQUIRES_NEW}),
 * so a run commits one show at a time and an interruption keeps everything written before it. The show
 * counterpart of {@link JdbcMovieStaging}; a show has no runtime, set or collection but does carry a
 * status.
 */
@Repository
class JdbcShowStaging implements ShowStaging {

    private static final String INSERT_SHOW = """
            INSERT INTO show_staging (
                title, original_title, year, normalized_title, normalized_original_title,
                status, rating_source, rating_value, rating_max, rating_votes,
                genres, plot, imdb_id, tvdb_id, tmdb_id, raw_nfo, slug, source_path,
                derived_title, derived_year)
            VALUES (
                :title, :originalTitle, :year, :normalizedTitle, :normalizedOriginalTitle,
                :status, :ratingSource, :ratingValue, :ratingMax, :ratingVotes,
                :genres, :plot, :imdbId, :tvdbId, :tmdbId, :rawNfo, :slug, :sourcePath,
                :derivedTitle, FALSE)
            RETURNING id
            """;

    private static final String INSERT_RATING = """
            INSERT INTO show_rating_staging (show_id, source, value, max, votes)
            VALUES (:showId, :source, :value, :max, :votes)
            """;

    private static final String INSERT_EPISODE = """
            INSERT INTO episode_staging (show_id, season_number, episode_number)
            VALUES (:showId, :seasonNumber, :episodeNumber)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcShowStaging(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void clear() {
        // Children first: the staging FKs forbid deleting a show while its ratings or episodes remain.
        jdbc.getJdbcTemplate().update("DELETE FROM show_rating_staging");
        jdbc.getJdbcTemplate().update("DELETE FROM episode_staging");
        jdbc.getJdbcTemplate().update("DELETE FROM show_staging");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long stage(StagedShow show) {
        ParsedRating inline = show.defaultRating();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("title", show.title())
                .addValue("derivedTitle", show.derivedTitle())
                .addValue("originalTitle", show.originalTitle())
                .addValue("year", show.year())
                .addValue("normalizedTitle", show.normalizedTitle())
                .addValue("normalizedOriginalTitle", show.normalizedOriginalTitle())
                .addValue("status", show.status().name())
                .addValue("ratingSource", inline == null ? null : inline.source())
                .addValue("ratingValue", inline == null ? null : inline.value())
                .addValue("ratingMax", inline == null ? null : inline.max())
                .addValue("ratingVotes", inline == null ? null : inline.votes())
                .addValue("genres", show.genresStorage())
                .addValue("plot", show.plot())
                .addValue("imdbId", show.imdbId())
                .addValue("tvdbId", show.tvdbId())
                .addValue("tmdbId", show.tmdbId())
                .addValue("rawNfo", show.rawNfo())
                .addValue("slug", show.slug())
                .addValue("sourcePath", show.sourcePath());

        Long showId = jdbc.queryForObject(INSERT_SHOW, params, Long.class);

        for (ParsedRating rating : show.ratings()) {
            jdbc.update(INSERT_RATING, new MapSqlParameterSource()
                    .addValue("showId", showId)
                    .addValue("source", rating.source())
                    .addValue("value", rating.value())
                    .addValue("max", rating.max())
                    .addValue("votes", rating.votes()));
        }
        return showId;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stageEpisodes(long showId, List<StagedEpisode> episodes) {
        if (episodes.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = episodes.stream()
                .map(episode -> new MapSqlParameterSource()
                        .addValue("showId", showId)
                        .addValue("seasonNumber", episode.seasonNumber())
                        .addValue("episodeNumber", episode.episodeNumber()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_EPISODE, batch);
    }
}
