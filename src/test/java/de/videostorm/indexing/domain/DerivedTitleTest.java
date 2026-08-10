package de.videostorm.indexing.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deriving a title from a folder name when metadata offers none: a trailing media extension is
 * stripped, while a year suffix or a non-media dot is left intact so the year is never read off a name.
 */
class DerivedTitleTest {

    @ParameterizedTest
    @CsvSource({
            "The Matrix.mkv,The Matrix",
            "Home Movie.MP4,Home Movie",
            "Notes.nfo,Notes",
    })
    void stripsATrailingMediaExtension(String folderName, String expected) {
        assertThat(DerivedTitle.fromFolderName(folderName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "Taken 3 (2014),Taken 3 (2014)",
            "Amelie.2001,Amelie.2001",
            "S.W.A.T,S.W.A.T",
            "The Thing,The Thing",
    })
    void leavesAYearSuffixOrNonMediaDotAlone(String folderName, String expected) {
        assertThat(DerivedTitle.fromFolderName(folderName)).isEqualTo(expected);
    }
}
