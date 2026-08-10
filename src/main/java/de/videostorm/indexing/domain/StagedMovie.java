package de.videostorm.indexing.domain;

import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.TitleNormalizer;

import java.util.List;

/**
 * A movie ready to be written to the staging tables: the parsed Emby fields plus the catalogue-shaped
 * derivations the live schema expects — the normalised sort and search keys, the identity slug, and
 * the delimiter-padded genre column. A missing year is stored as {@code 0} (rendered blank, never
 * matched by search); it is never guessed from a filename.
 *
 * <p>{@link #defaultRating()} is the single provider whose score the listing shows inline on the
 * movie row — the one Emby flagged {@code default="true"}, or the first if none is. {@link #ratings()}
 * carries every provider for the ratings child table.
 */
public record StagedMovie(
        String title,
        String originalTitle,
        int year,
        String normalizedTitle,
        String normalizedOriginalTitle,
        String slug,
        String sourcePath,
        String rawNfo,
        String genresStorage,
        Integer runtimeMinutes,
        String plot,
        String setName,
        String collectionId,
        String imdbId,
        String tvdbId,
        String tmdbId,
        ParsedRating defaultRating,
        List<ParsedRating> ratings) {

    public StagedMovie {
        ratings = ratings == null ? List.of() : List.copyOf(ratings);
    }

    public static StagedMovie from(ParsedMovie parsed, String sourcePath, String rawNfo) {
        int year = parsed.year() == null ? 0 : parsed.year();
        String originalTitle = parsed.originalTitle();
        return new StagedMovie(
                parsed.title(),
                originalTitle,
                year,
                TitleNormalizer.normalize(parsed.title()),
                originalTitle == null ? null : TitleNormalizer.normalize(originalTitle),
                Slug.forMovie(parsed.title(), year),
                sourcePath,
                rawNfo,
                new GenreList(parsed.genres()).toStorage(),
                parsed.runtimeMinutes(),
                parsed.plot(),
                parsed.setName(),
                parsed.collectionId(),
                parsed.imdbId(),
                parsed.tvdbId(),
                parsed.tmdbId(),
                defaultRating(parsed.ratings()),
                parsed.ratings());
    }

    private static ParsedRating defaultRating(List<ParsedRating> ratings) {
        return ratings.stream()
                .filter(ParsedRating::isDefault)
                .findFirst()
                .orElse(ratings.isEmpty() ? null : ratings.get(0));
    }
}
