package de.videostorm.indexing.domain;

import de.videostorm.sources.domain.SourceType;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Renders a run's issue detail as a spreadsheet-ready CSV: the columns are type, issue type, path,
 * title and field, one row per {@link RunIssue}.
 *
 * <p>The bytes are UTF-8 led by a byte order mark and the columns are semicolon-separated, the shape
 * Excel in a German locale opens with the columns intact and umlauts correct. Fields are quoted per
 * RFC 4180 — only when they hold a separator, a quote or a line break — with any internal quote
 * doubled, so a path or title carrying one of those can never split a row or a column.
 */
public final class RunReportCsv {

    private static final char BOM = '﻿';
    private static final char SEPARATOR = ';';
    private static final String NEWLINE = "\r\n";
    private static final List<String> HEADER = List.of("type", "issue type", "path", "title", "field");

    private RunReportCsv() {
    }

    /** The CSV bytes for {@code issues} recorded against a run of {@code type}; header only when empty. */
    public static byte[] render(SourceType type, List<RunIssue> issues) {
        StringBuilder csv = new StringBuilder();
        csv.append(BOM);
        appendRow(csv, HEADER);
        String typeLabel = type.plural();
        for (RunIssue issue : issues) {
            appendRow(csv, List.of(
                    typeLabel,
                    issue.type().name(),
                    nullToEmpty(issue.path()),
                    nullToEmpty(issue.title()),
                    nullToEmpty(issue.field())));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder csv, List<String> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(escape(columns.get(i)));
        }
        csv.append(NEWLINE);
    }

    private static String escape(String value) {
        boolean mustQuote = value.indexOf(SEPARATOR) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!mustQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
