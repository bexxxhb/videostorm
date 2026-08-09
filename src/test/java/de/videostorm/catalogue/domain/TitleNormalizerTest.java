package de.videostorm.catalogue.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TitleNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "Über,uber",
            "Ärger,arger",
            "'96 Hours - Taken 3','96 hours taken 3'",
            "The Thing,the thing",
            "'  Leading and trailing  ','leading and trailing'",
            "École,ecole",
            "Amélie,amelie",
            "'---',''",
    })
    void normalizesToLowercaseAsciiWithCollapsedSeparators(String title, String expected) {
        assertThat(TitleNormalizer.normalize(title)).isEqualTo(expected);
    }

    @Test
    void blankInputNormalizesToEmptyString() {
        assertThat(TitleNormalizer.normalize("")).isEmpty();
    }

    @Test
    void nullInputNormalizesToEmptyString() {
        assertThat(TitleNormalizer.normalize(null)).isEmpty();
    }
}
