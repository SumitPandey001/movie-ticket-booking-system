package com.sumit.movieticketbookingsystem.booking.internal.refund;

import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundQuote;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundRule;

import java.time.Duration;
import java.util.List;

/**
 * The first slab whose hours the customer is still ahead of decides the percentage. Only whole hours count, so
 * 23h59m before the show is 23 hours; with less time left than the smallest slab, nothing is refunded.
 *
 * @param slabs most hours first, as policies store them
 */
record SlabRefundRule(List<RefundSlab> slabs, boolean refundFees) implements RefundRule {

    @Override
    public RefundQuote quote(List<BookingSeat> seats, Duration beforeShow) {
        long hours = beforeShow.toHours();
        int percent = slabs.stream()
                .filter(slab -> hours >= slab.minHoursBefore())
                .findFirst()
                .map(RefundSlab::percent)
                .orElse(0);
        return RefundRule.refund(seats, percent, refundFees);
    }
}
