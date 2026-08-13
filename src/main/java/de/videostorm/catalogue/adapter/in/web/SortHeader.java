package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.SortDirection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * One sortable column header: the link that (re-)sorts by it and the glyph shown beside its label. The
 * link points at this column with the direction a click should produce — the opposite of the active
 * direction when this column is already active (a toggle), ascending otherwise. The marker reflects the
 * current state: {@code ▲}/{@code ▼} on the active column, a neutral {@code ↕} on the others. The
 * active search query is carried so re-sorting preserves it; page is deliberately dropped so re-sorting
 * lands on page 1.
 *
 * <p>Plain JavaBean rather than a record — Pug4j resolves model properties via {@code getXxx()}.
 */
@Getter
@RequiredArgsConstructor
public class SortHeader {

    static final String NEUTRAL_MARKER = "↕";
    static final String ASC_MARKER = "▲";
    static final String DESC_MARKER = "▼";

    private final String url;
    private final String marker;

    static SortHeader of(String basePath, String columnParam, boolean active,
                         SortDirection activeDirection, String query) {
        SortDirection linkDirection = active ? activeDirection.opposite() : SortDirection.ASC;
        String marker = active
                ? (activeDirection == SortDirection.ASC ? ASC_MARKER : DESC_MARKER)
                : NEUTRAL_MARKER;
        return new SortHeader(url(basePath, columnParam, linkDirection, query), marker);
    }

    private static String url(String basePath, String columnParam, SortDirection direction, String query) {
        StringBuilder url = new StringBuilder(basePath)
                .append("?sort=").append(columnParam)
                .append("&dir=").append(direction.param());
        if (query != null && !query.isBlank()) {
            url.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        }
        return url.toString();
    }
}
