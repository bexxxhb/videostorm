package de.videostorm.catalogue.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShowStatusTest {

    @Test
    void endedRendersLowercase() {
        assertThat(ShowStatus.ENDED.displayLabel()).isEqualTo("ended");
    }

    @Test
    void continuingRendersLowercase() {
        assertThat(ShowStatus.CONTINUING.displayLabel()).isEqualTo("continuing");
    }

    @Test
    void unknownRendersLowercase() {
        assertThat(ShowStatus.UNKNOWN.displayLabel()).isEqualTo("unknown");
    }

    @Test
    void mapsEndedAndContinuingCaseAndWhitespaceInsensitively() {
        assertThat(ShowStatus.fromNfo("Ended")).isEqualTo(ShowStatus.ENDED);
        assertThat(ShowStatus.fromNfo("  continuing ")).isEqualTo(ShowStatus.CONTINUING);
        assertThat(ShowStatus.fromNfo("ENDED")).isEqualTo(ShowStatus.ENDED);
    }

    @Test
    void defaultsToUnknownForAbsentBlankOrUnrecognisedValues() {
        assertThat(ShowStatus.fromNfo(null)).isEqualTo(ShowStatus.UNKNOWN);
        assertThat(ShowStatus.fromNfo("")).isEqualTo(ShowStatus.UNKNOWN);
        assertThat(ShowStatus.fromNfo("Cancelled")).isEqualTo(ShowStatus.UNKNOWN);
    }
}
