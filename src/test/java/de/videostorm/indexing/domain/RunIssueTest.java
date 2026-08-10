package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunIssueTest {

    @Test
    void aFieldGapNamesTheThinField() {
        RunIssue issue = RunIssue.missingField("/m/Blob", "The Blob", RunIssue.YEAR_FIELD);

        assertThat(issue.type()).isEqualTo(RunIssueType.MISSING_FIELD);
        assertThat(issue.path()).isEqualTo("/m/Blob");
        assertThat(issue.title()).isEqualTo("The Blob");
        assertThat(issue.field()).isEqualTo("year");
    }

    @Test
    void aDroppedOrEmptyFolderIssueCarriesNoField() {
        assertThat(RunIssue.noVideo("/m/Just Metadata", "Stranded").field()).isNull();
        assertThat(RunIssue.ignoredVideo("/m/Dune/trailer.mp4", "Dune").field()).isNull();
    }

    @Test
    void refusesABlankPath() {
        assertThatThrownBy(() -> RunIssue.noVideo("  ", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
