package com.sumit.movieticketbookingsystem.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatRefTest {

    @Test
    void parsesAndFormatsLabels() {
        assertThat(SeatRef.parse("F7")).isEqualTo(new SeatRef("F", 7));
        assertThat(SeatRef.parse("AA12")).isEqualTo(new SeatRef("AA", 12));
        assertThat(new SeatRef("J", 8).label()).isEqualTo("J8");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"7F", "f7", "F", "7", "F0", "ABCDE1", "F-7"})
    void rejectsInvalidLabels(String label) {
        assertThatThrownBy(() -> SeatRef.parse(label)).isInstanceOf(IllegalArgumentException.class);
    }
}
