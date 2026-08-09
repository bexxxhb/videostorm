package de.videostorm.catalogue.adapter.in.web;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The first/previous/next/last targets for one page, or {@code null} where that control is
 * disabled — computed once here rather than as four parallel ternaries in the controller. Every
 * link carries the active search query so paging preserves it. Aggregate-agnostic: the caller
 * supplies its own route as {@code basePath}, so one class serves every tab's pagination.
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

    static PaginationLinks from(
            int pageNumber, int totalPages, boolean hasPrevious, boolean hasNext, String query, String basePath) {
        return new PaginationLinks(
                hasPrevious ? pageUrl(1, query, basePath) : null,
                hasPrevious ? pageUrl(pageNumber - 1, query, basePath) : null,
                hasNext ? pageUrl(pageNumber + 1, query, basePath) : null,
                hasNext ? pageUrl(totalPages, query, basePath) : null);
    }

    private static String pageUrl(int page, String query, String basePath) {
        if (query == null || query.isBlank()) {
            return basePath + "?page=" + page;
        }
        return basePath + "?page=" + page + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
