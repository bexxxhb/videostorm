package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.domain.Movie;
import de.videostorm.catalogue.domain.Rating;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * The template is kept dumb: every string here is already display-ready, including the zebra
 * row class, so Pug never has to evaluate arithmetic or ternaries.
 *
 * <p>Plain JavaBean rather than a record — Pug4j resolves model properties via {@code getXxx()},
 * which record accessors ({@code xxx()}) don't satisfy.
 */
@Getter
@RequiredArgsConstructor
public class MovieRow {

    private static final String EVEN_ROW_CLASS = "row-even";
    private static final String ODD_ROW_CLASS = "row-odd";
    private static final String IMDB_LINK_TEXT = "info @ IMDB.com";

    // 1-based running position in the full result set, precomputed as a string so the template renders
    // the leftmost index cell without any arithmetic.
    private final String indexDisplay;
    private final String rowClass;
    private final String title;
    private final String year;
    private final String ratingDisplay;
    private final String genresDisplay;
    private final String genresFullText;
    private final String runtimeDisplay;
    // Empty when the feature filename carried no recognised resolution token; always p-suffixed otherwise.
    private final String resolutionDisplay;
    // Both empty when the film has no imdb id, so the template renders an empty cell (no link, no text).
    private final String imdbUrl;
    private final String imdbLinkText;
    // The Plot link renders only when true; the dialog reads the plot from plotBase64 (UTF-8 bytes,
    // base64-encoded) so quotes, angle brackets, accents and newlines survive the attribute round-trip.
    private final boolean hasPlot;
    private final String plotBase64;
    // The "Raw data" link renders only when true; the dialog fetches the (potentially large) raw .nfo
    // from rawNfoUrl on demand, so the listing never carries the text for every row.
    private final boolean hasRawNfo;
    private final String rawNfoUrl;

    static MovieRow from(Movie movie, int index, long number) {
        Optional<String> plot = movie.plot().filter(text -> !text.isBlank());
        Optional<String> imdbId = movie.imdbId().filter(id -> !id.isBlank());
        return new MovieRow(
                String.valueOf(number),
                index % 2 == 0 ? EVEN_ROW_CLASS : ODD_ROW_CLASS,
                movie.title(),
                movie.year().isKnown() ? String.valueOf(movie.year().value()) : "",
                movie.rating().map(Rating::displayLabel).orElse(""),
                movie.genres().displayLabel(),
                movie.genres().fullText(),
                movie.runtimeMinutes().map(String::valueOf).orElse(""),
                movie.resolution().orElse(""),
                imdbId.map(id -> "https://www.imdb.com/title/" + id + "/").orElse(""),
                imdbId.isPresent() ? IMDB_LINK_TEXT : "",
                plot.isPresent(),
                plot.map(MovieRow::encodeBase64).orElse(""),
                movie.hasRawNfo(),
                movie.hasRawNfo() ? "/movies/" + movie.id() + "/nfo" : "");
    }

    private static String encodeBase64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
