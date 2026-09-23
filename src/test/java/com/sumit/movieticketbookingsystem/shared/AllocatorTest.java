package com.sumit.movieticketbookingsystem.shared;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocatorTest {

    @Test
    void splitsEvenlyWhenWeightsAreEqual() {
        assertThat(Allocator.largestRemainder(5000, List.of(36000L, 36000L))).containsExactly(2500, 2500);
    }

    @Test
    void leftoverPaiseGoToTheLargestRemainders() {
        // 100 / 3 = 33.33 each, so one part has to take the extra paisa
        assertThat(Allocator.largestRemainder(100, List.of(1L, 1L, 1L))).containsExactly(34, 33, 33);

        // 10 * 1/6 = 1.67, 10 * 2/6 = 3.33, 10 * 3/6 = 5.0 -> the first part has the biggest remainder
        assertThat(Allocator.largestRemainder(10, List.of(1L, 2L, 3L))).containsExactly(2, 3, 5);
    }

    @Test
    void partsAlwaysAddUpToTheTotal() {
        List<Long> weights = List.of(30000L, 36000L, 42000L, 18000L);
        for (long total = 0; total <= 1000; total++) {
            long[] parts = Allocator.largestRemainder(total, weights);
            assertThat(Arrays.stream(parts).sum()).isEqualTo(total);
        }
    }

    @Test
    void zeroWeightGetsNothing() {
        assertThat(Allocator.largestRemainder(999, List.of(0L, 1L))).containsExactly(0, 999);
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> Allocator.largestRemainder(10, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Allocator.largestRemainder(10, List.of(0L, 0L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Allocator.largestRemainder(-1, List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Allocator.largestRemainder(10, List.of(-1L, 2L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
