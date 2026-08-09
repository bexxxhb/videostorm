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
}
