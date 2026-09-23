package com.sumit.movieticketbookingsystem.shared;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeWindowTest {

    private final Instant noon = Instant.parse("2026-10-03T12:00:00Z");
    private final Instant fourPm = Instant.parse("2026-10-03T16:00:00Z");

    @Test
    void includesStartButNotEnd() {
        TimeWindow afternoon = new TimeWindow(noon, fourPm);

        assertThat(afternoon.contains(noon)).isTrue();
        assertThat(afternoon.contains(fourPm.minusSeconds(1))).isTrue();
        assertThat(afternoon.contains(fourPm)).isFalse();
        assertThat(afternoon.contains(noon.minusSeconds(1))).isFalse();
    }

    @Test
    void startMustBeBeforeEnd() {
        assertThatThrownBy(() -> new TimeWindow(fourPm, noon)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeWindow(noon, noon)).isInstanceOf(IllegalArgumentException.class);
    }
}
