package de.videostorm.indexing.domain;

import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.ShowStatus;
import de.videostorm.catalogue.domain.TitleNormalizer;

import java.util.List;

/**
 * A show ready to be written to the staging tables: the parsed Emby fields plus the catalogue-shaped
 * derivations the live schema expects — the normalised sort and search keys, the identity slug, the
 * delimiter-padded genre column, the year read from the premiered date and the mapped status. A
 * missing year is stored as {@code 0} (rendered blank, never matched by search); it is never guessed
 * from a folder name. Shows carry no runtime, set or collection — those are movie-only fields.
 *
 * <p>{@link #defaultRating()} is the single provider whose score the listing shows inline on the show
 * row — the one Emby flagged {@code default="true"}, or the first if none is. {@link #ratings()}
 * carries every provider for the ratings child table.
 */
public record StagedShow(
        String title,
        boolean derivedTitle,
        String originalTitle,
        int year,
        String normalizedTitle,
        String normalizedOriginalTitle,
        ShowStatus status,
        String slug,
        String sourcePath,
        String rawNfo,
        String genresStorage,
        String plot,
        String imdbId,
        String tvdbId,
        String tmdbId,
        ParsedRating defaultRating,
        List<ParsedRating> ratings) {

    public StagedShow {
        ratings = ratings == null ? List.of() : List.copyOf(ratings);
    }

    /**
     * Builds the staged show from what was parsed plus its folder context. A metadata title is used
     * as-is; where none was found the folder name (extension stripped) becomes the title and the entry
     * is flagged {@link #derivedTitle()}. The normalised keys and identity slug are always computed
     * from the effective title, so a derived title still sorts, searches and identifies correctly. The
     * year is derived from the premiered date and the status mapped to one of three states.
     */
    public static StagedShow from(ParsedShow parsed, String folderName, String sourcePath, String rawNfo) {
        boolean derivedTitle = parsed.title() == null;
        String title = DerivedTitle.resolve(parsed.title(), folderName);
        int year = PremieredYear.from(parsed.premiered());
        String originalTitle = parsed.originalTitle();
        return new StagedShow(
                title,
                derivedTitle,
                originalTitle,
                year,
                TitleNormalizer.normalize(title),
                originalTitle == null ? null : TitleNormalizer.normalize(originalTitle),
                ShowStatus.fromNfo(parsed.status()),
                Slug.of(title, year),
                sourcePath,
                rawNfo,
                new GenreList(parsed.genres()).toStorage(),
                parsed.plot(),
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
