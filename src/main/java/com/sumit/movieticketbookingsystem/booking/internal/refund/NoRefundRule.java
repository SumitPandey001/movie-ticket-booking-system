package com.sumit.movieticketbookingsystem.booking.internal.refund;

import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundQuote;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundRule;

import java.time.Duration;
import java.util.List;

/** Non-refundable tickets: the customer may still cancel (freeing the seats) but gets nothing back. */
enum NoRefundRule implements RefundRule {
    INSTANCE;

    @Override
    public RefundQuote quote(List<BookingSeat> seats, Duration beforeShow) {
        return RefundRule.refund(seats, 0, false);
    }
}
