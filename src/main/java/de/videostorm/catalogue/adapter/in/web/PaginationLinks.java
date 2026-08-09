package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.MoviePage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The first/previous/next/last targets for one page, or {@code null} where that control is
 * disabled — computed once here rather than as four parallel ternaries in the controller.
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
        return new PaginationLinks(
                moviePage.hasPrevious() ? pageUrl(1) : null,
                moviePage.hasPrevious() ? pageUrl(moviePage.pageNumber() - 1) : null,
                moviePage.hasNext() ? pageUrl(moviePage.pageNumber() + 1) : null,
                moviePage.hasNext() ? pageUrl(moviePage.totalPages()) : null);
    }

    private static String pageUrl(int page) {
        return "/movies?page=" + page;
    }
}
