package com.sumit.movieticketbookingsystem.booking.internal.refund;

import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundQuote;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRuleTest {

    // two ₹200 seats: ticket ₹236 each (with GST), fee ₹23.60 each (₹20 + GST)
    private static final List<BookingSeat> SEATS = List.of(
            new BookingSeat(11, "F7", 1, 20000, 0, 2360, 25960),
            new BookingSeat(12, "F8", 1, 20000, 0, 2360, 25960));

    private static final List<RefundSlab> STANDARD = List.of(
            new RefundSlab(24, 100), new RefundSlab(4, 50), new RefundSlab(0, 0));

    @Test
    void slabBoundariesCountWholeHours() {
        SlabRefundRule rule = new SlabRefundRule(STANDARD, false);

        assertThat(percent(rule, Duration.ofHours(24))).isEqualTo(100);
        assertThat(percent(rule, Duration.ofHours(24).minusMinutes(1))).isEqualTo(50);
        assertThat(percent(rule, Duration.ofHours(4))).isEqualTo(50);
        assertThat(percent(rule, Duration.ofHours(4).minusMinutes(1))).isEqualTo(0);
        assertThat(percent(rule, Duration.ofMinutes(-5))).isEqualTo(0);            // already started
    }

    @Test
    void feesComeBackOnlyWhenThePolicySaysSo() {
        RefundQuote ticketOnly = new SlabRefundRule(STANDARD, false).quote(SEATS, Duration.ofDays(2));
        assertThat(ticketOnly.refundPaise()).isEqualTo(2 * 23600);
        assertThat(ticketOnly.retainedPaise()).isEqualTo(2 * 2360);

        RefundQuote withFees = new SlabRefundRule(STANDARD, true).quote(SEATS, Duration.ofDays(2));
        assertThat(withFees.refundPaise()).isEqualTo(51920);
        assertThat(withFees.retainedPaise()).isZero();
    }

    @Test
    void halfRefundRoundsToThePaisa() {
        List<BookingSeat> odd = List.of(new BookingSeat(1, "A1", 1, 20000, 0, 2360, 25961));

        RefundQuote quote = new SlabRefundRule(STANDARD, false).quote(odd, Duration.ofHours(5));

        assertThat(quote.refundPaise()).isEqualTo(11801);                         // 50% of 23601, half up
        assertThat(quote.refundPaise() + quote.retainedPaise()).isEqualTo(25961);
    }

    @Test
    void fullAndNoRefund() {
        assertThat(FullRefundRule.INSTANCE.quote(SEATS, Duration.ZERO).refundPaise()).isEqualTo(51920);
        RefundQuote nothing = NoRefundRule.INSTANCE.quote(SEATS, Duration.ofDays(9));
        assertThat(nothing.refundPaise()).isZero();
        assertThat(nothing.retainedPaise()).isEqualTo(51920);
    }

    @Test
    void aSnapshotPicksItsRule() {
        assertThat(new RefundPolicySnapshot(1, "Std", RefundPolicyType.SLAB, false, STANDARD).refundRule())
                .isEqualTo(new SlabRefundRule(STANDARD, false));
        assertThat(new RefundPolicySnapshot(2, "None", RefundPolicyType.NON_REFUNDABLE, false, List.of())
                .refundRule()).isSameAs(NoRefundRule.INSTANCE);
    }

    private static int percent(SlabRefundRule rule, Duration beforeShow) {
        return rule.quote(SEATS, beforeShow).refundPercent();
    }
}
