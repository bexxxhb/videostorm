package de.videostorm.indexing.domain;

import de.videostorm.sources.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunReportCsvTest {

    private static String decode(byte[] csv) {
        return new String(csv, StandardCharsets.UTF_8);
    }

    @Test
    void beginsWithAUtf8ByteOrderMark() {
        byte[] csv = RunReportCsv.render(SourceType.MOVIES, List.of());

        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void carriesTheSemicolonSeparatedHeaderEvenWithNoIssues() {
        String csv = decode(RunReportCsv.render(SourceType.MOVIES, List.of()));

        assertThat(csv).isEqualTo("﻿type;issue type;path;title;field\r\n");
    }

    @Test
    void rendersOneSemicolonSeparatedRowPerIssueWithTheRunType() {
        String csv = decode(RunReportCsv.render(SourceType.MOVIES, List.of(
                RunIssue.missingField("/m/Blob", "The Blob", RunIssue.TITLE_FIELD))));

        assertThat(csv).contains("Movies;MISSING_FIELD;/m/Blob;The Blob;title\r\n");
    }

    @Test
    void namesTheShowTypeForShowRuns() {
        String csv = decode(RunReportCsv.render(SourceType.SHOWS, List.of(
                RunIssue.skippedEpisode("/s/Show/ep.mkv", "Show"))));

        assertThat(csv).contains("Shows;SKIPPED_EPISODE;/s/Show/ep.mkv;Show;\r\n");
    }

    @Test
    void leavesAbsentTitleAndFieldAsEmptyColumns() {
        String csv = decode(RunReportCsv.render(SourceType.MOVIES, List.of(
                new RunIssue(RunIssueType.NO_VIDEO, "/m/x", null, null))));

        assertThat(csv).contains("Movies;NO_VIDEO;/m/x;;\r\n");
    }

    @Test
    void quotesFieldsHoldingASeparatorAndDoublesInternalQuotes() {
        String csv = decode(RunReportCsv.render(SourceType.MOVIES, List.of(
                RunIssue.missingField("/m/a;b", "The \"Real\" Deal", RunIssue.TITLE_FIELD))));

        assertThat(csv).contains("Movies;MISSING_FIELD;\"/m/a;b\";\"The \"\"Real\"\" Deal\";title\r\n");
    }

    @Test
    void quotesFieldsHoldingANewlineSoRowsStayIntact() {
        String csv = decode(RunReportCsv.render(SourceType.MOVIES, List.of(
                RunIssue.noVideo("/m/line\nbreak", "Split"))));

        assertThat(csv).contains("\"/m/line\nbreak\"");
    }

    @Test
    void keepsUmlautsIntactUnderUtf8() {
        byte[] csv = RunReportCsv.render(SourceType.MOVIES, List.of(
                RunIssue.missingField("/m/Käfer", "Käfer", RunIssue.YEAR_FIELD)));

        assertThat(decode(csv)).contains("Käfer");
    }
}
