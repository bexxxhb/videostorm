package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.MoviePage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The first/previous/next/last targets for one page, or {@code null} where that control is
 * disabled — computed once here rather than as four parallel ternaries in the controller. Every
 * link carries the active search query so paging preserves it.
 *
 * <p>Plain JavaBean rather than a record — Pug4j resolves model properties via {@code getXxx()}.
 */
@Getter
@RequiredArgsConstructor
public class PaginationLinks {

    private final String firstPageUrl;
    private final String previousPageUrl;
    private final String nextPageUrl;
    private final String lastPageUrl;

    static PaginationLinks from(MoviePage moviePage) {
        String query = moviePage.query();
        return new PaginationLinks(
                moviePage.hasPrevious() ? pageUrl(1, query) : null,
                moviePage.hasPrevious() ? pageUrl(moviePage.pageNumber() - 1, query) : null,
                moviePage.hasNext() ? pageUrl(moviePage.pageNumber() + 1, query) : null,
                moviePage.hasNext() ? pageUrl(moviePage.totalPages(), query) : null);
    }

    private static String pageUrl(int page, String query) {
        if (query == null || query.isBlank()) {
            return "/movies?page=" + page;
        }
        return "/movies?page=" + page + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
