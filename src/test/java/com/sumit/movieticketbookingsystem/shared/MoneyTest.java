package com.sumit.movieticketbookingsystem.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.ofPaise(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minusBelowZeroIsRejected() {
        assertThatThrownBy(() -> Money.ofPaise(100).minus(Money.ofPaise(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plusFailsOnOverflowInsteadOfWrapping() {
        assertThatThrownBy(() -> Money.ofPaise(Long.MAX_VALUE).plus(Money.ofPaise(1)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void percentRoundsHalfUp() {
        // 18% GST on the LLD worked example
        assertThat(Money.ofPaise(33500).percent(18)).isEqualTo(Money.ofPaise(6030));
        assertThat(Money.ofPaise(2000).percent(18)).isEqualTo(Money.ofPaise(360));

        assertThat(Money.ofPaise(25).percent(10)).isEqualTo(Money.ofPaise(3));   // 2.5 -> 3
        assertThat(Money.ofPaise(24).percent(10)).isEqualTo(Money.ofPaise(2));   // 2.4 -> 2
    }

    @Test
    void percentOfZeroAndHundred() {
        assertThat(Money.ofPaise(39530).percent(0)).isEqualTo(Money.ZERO);
        assertThat(Money.ofPaise(39530).percent(100)).isEqualTo(Money.ofPaise(39530));
    }

    @Test
    void minPicksTheSmallerAmount() {
        assertThat(Money.ofPaise(5000).min(Money.ofPaise(3000))).isEqualTo(Money.ofPaise(3000));
        assertThat(Money.ofPaise(3000).min(Money.ofPaise(5000))).isEqualTo(Money.ofPaise(3000));
    }
}
