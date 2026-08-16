package de.videostorm.indexing.domain;

import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.TitleNormalizer;

import java.util.List;

/**
 * A movie ready to be written to the staging tables: the parsed Emby fields plus the catalogue-shaped
 * derivations the live schema expects — the normalised sort and search keys, the identity slug, and
 * the delimiter-padded genre column. A missing year is stored as {@code 0} (rendered blank, never
 * matched by search); it is never guessed from a filename. The {@code resolution} is the exception the
 * other way round: it is derived by the scan purely from the feature video's filename (already normalised
 * to the {@code p}-suffixed form), since the {@code .nfo} holds no resolution, and is {@code null} when
 * the filename carried no recognised token.
 *
 * <p>{@link #defaultRating()} is the single provider whose score the listing shows inline on the
 * movie row — the one Emby flagged {@code default="true"}, or the first if none is. {@link #ratings()}
 * carries every provider for the ratings child table.
 */
public record StagedMovie(
        String title,
        boolean derivedTitle,
        String originalTitle,
        int year,
        String normalizedTitle,
        String normalizedOriginalTitle,
        String slug,
        String sourcePath,
        long sizeBytes,
        String rawNfo,
        String genresStorage,
        Integer runtimeMinutes,
        String resolution,
        String plot,
        String setName,
        String collectionId,
        String imdbId,
        String tvdbId,
        String tmdbId,
        ParsedRating defaultRating,
        List<ParsedRating> ratings,
        List<ParsedActor> actors) {

    public StagedMovie {
        ratings = ratings == null ? List.of() : List.copyOf(ratings);
        actors = actors == null ? List.of() : List.copyOf(actors);
    }

    /**
     * The normalised title a film is identified by: the original title where one was parsed, else the
     * display title (which itself already falls back to the folder-derived name). Mirrors the database's
     * {@code COALESCE(normalized_original_title, normalized_title)} identity index, so the run's own
     * duplicate detection and the schema backstop can never disagree on what counts as the same film.
     */
    public String normalizedIdentityTitle() {
        return normalizedOriginalTitle != null ? normalizedOriginalTitle : normalizedTitle;
    }

    /**
     * Builds the staged movie from what was parsed plus its folder context. A metadata title is used
     * as-is; where none was found the folder name (extension stripped) becomes the title and the entry
     * is flagged {@link #derivedTitle()}. The normalised keys and identity slug are always computed
     * from the effective title, so a derived title still sorts, searches and identifies correctly.
     */
    public static StagedMovie from(ParsedMovie parsed, String folderName, String sourcePath, long sizeBytes,
                                   String rawNfo, String resolution) {
        boolean derivedTitle = parsed.title() == null;
        String title = DerivedTitle.resolve(parsed.title(), folderName);
        int year = parsed.year() == null ? 0 : parsed.year();
        String originalTitle = parsed.originalTitle();
        return new StagedMovie(
                title,
                derivedTitle,
                originalTitle,
                year,
                TitleNormalizer.normalize(title),
                originalTitle == null ? null : TitleNormalizer.normalize(originalTitle),
                Slug.of(title, year),
                sourcePath,
                sizeBytes,
                rawNfo,
                new GenreList(parsed.genres()).toStorage(),
                parsed.runtimeMinutes(),
                resolution,
                parsed.plot(),
                parsed.setName(),
                parsed.collectionId(),
                parsed.imdbId(),
                parsed.tvdbId(),
                parsed.tmdbId(),
                defaultRating(parsed.ratings()),
                parsed.ratings(),
                parsed.actors());
    }

    private static ParsedRating defaultRating(List<ParsedRating> ratings) {
        return ratings.stream()
                .filter(ParsedRating::isDefault)
                .findFirst()
                .orElse(ratings.isEmpty() ? null : ratings.get(0));
    }
}
