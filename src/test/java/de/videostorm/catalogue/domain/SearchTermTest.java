package de.videostorm.catalogue.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTermTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void nullEmptyAndWhitespaceOnlyInputIsBlank(String raw) {
        assertThat(new SearchTerm(raw).isBlank()).isTrue();
    }

    @Test
    void nonBlankInputIsNotBlank() {
        assertThat(new SearchTerm("Taken").isBlank()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "madchen,madchen",
            "Madchen,madchen",
            "' Taken 3 ',taken 3",
            "'96 Hours - Taken 3','96 hours taken 3'",
    })
    void normalizedTitleReusesTheSharedNormalizationRule(String raw, String expected) {
        assertThat(new SearchTerm(raw).normalizedTitle()).isEqualTo(expected);
    }

    @Test
    void blankInputNormalizesToAnEmptyTitle() {
        assertThat(new SearchTerm("").normalizedTitle()).isEmpty();
    }

    @Test
    void genreFragmentStripsTheStorageDelimiterSoATermCannotSpanTwoGenres() {
        assertThat(new SearchTerm("Action|Thriller").genreFragment()).isEqualTo("ActionThriller");
    }

    @Test
    void genreFragmentLeavesInputWithoutTheDelimiterUnchanged() {
        assertThat(new SearchTerm("hriller").genreFragment()).isEqualTo("hriller");
    }

    @ParameterizedTest
    @CsvSource({"1984", "0001", "9999"})
    void exactlyFourDigitsMatchesTheYear(String raw) {
        assertThat(new SearchTerm(raw).yearExactMatch()).isEqualTo(Optional.of(Integer.valueOf(raw)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12345", "19a4", "", "-1984", "19 84"})
    void numericTermsOfOtherLengthsOrNonDigitsNeverMatchTheYear(String raw) {
        assertThat(new SearchTerm(raw).yearExactMatch()).isEmpty();
    }

    @Test
    void surroundingWhitespaceIsTrimmedBeforeMatchingTheYear() {
        assertThat(new SearchTerm(" 1984 ").yearExactMatch()).isEqualTo(Optional.of(1984));
    }
}
