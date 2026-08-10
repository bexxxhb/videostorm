package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.indexing.application.port.out.MovieStaging;
import de.videostorm.indexing.domain.ParsedRating;
import de.videostorm.indexing.domain.StagedMovie;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes staging with {@link NamedParameterJdbcTemplate}: the importer never goes through JPA, which
 * reads the live tables only. Each {@link #stage} is its own transaction ({@code REQUIRES_NEW}), so a
 * run commits one movie at a time and an interruption keeps everything written before it.
 */
@Repository
class JdbcMovieStaging implements MovieStaging {

    private static final String INSERT_MOVIE = """
            INSERT INTO movie_staging (
                title, original_title, year, normalized_title, normalized_original_title,
                rating_source, rating_value, rating_max, rating_votes,
                genres, runtime_minutes, plot, set_name, collection_id,
                imdb_id, tvdb_id, tmdb_id, raw_nfo, slug, source_path,
                derived_title, derived_year)
            VALUES (
                :title, :originalTitle, :year, :normalizedTitle, :normalizedOriginalTitle,
                :ratingSource, :ratingValue, :ratingMax, :ratingVotes,
                :genres, :runtimeMinutes, :plot, :setName, :collectionId,
                :imdbId, :tvdbId, :tmdbId, :rawNfo, :slug, :sourcePath,
                FALSE, FALSE)
            RETURNING id
            """;

    private static final String INSERT_RATING = """
            INSERT INTO movie_rating_staging (movie_id, source, value, max, votes)
            VALUES (:movieId, :source, :value, :max, :votes)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcMovieStaging(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void clear() {
        // Child first: the staging FK forbids deleting a movie while its ratings remain.
        jdbc.getJdbcTemplate().update("DELETE FROM movie_rating_staging");
        jdbc.getJdbcTemplate().update("DELETE FROM movie_staging");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long stage(StagedMovie movie) {
        ParsedRating inline = movie.defaultRating();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("title", movie.title())
                .addValue("originalTitle", movie.originalTitle())
                .addValue("year", movie.year())
                .addValue("normalizedTitle", movie.normalizedTitle())
                .addValue("normalizedOriginalTitle", movie.normalizedOriginalTitle())
                .addValue("ratingSource", inline == null ? null : inline.source())
                .addValue("ratingValue", inline == null ? null : inline.value())
                .addValue("ratingMax", inline == null ? null : inline.max())
                .addValue("ratingVotes", inline == null ? null : inline.votes())
                .addValue("genres", movie.genresStorage())
                .addValue("runtimeMinutes", movie.runtimeMinutes())
                .addValue("plot", movie.plot())
                .addValue("setName", movie.setName())
                .addValue("collectionId", movie.collectionId())
                .addValue("imdbId", movie.imdbId())
                .addValue("tvdbId", movie.tvdbId())
                .addValue("tmdbId", movie.tmdbId())
                .addValue("rawNfo", movie.rawNfo())
                .addValue("slug", movie.slug())
                .addValue("sourcePath", movie.sourcePath());

        Long movieId = jdbc.queryForObject(INSERT_MOVIE, params, Long.class);

        for (ParsedRating rating : movie.ratings()) {
            jdbc.update(INSERT_RATING, new MapSqlParameterSource()
                    .addValue("movieId", movieId)
                    .addValue("source", rating.source())
                    .addValue("value", rating.value())
                    .addValue("max", rating.max())
                    .addValue("votes", rating.votes()));
        }
        return movieId;
    }
}
