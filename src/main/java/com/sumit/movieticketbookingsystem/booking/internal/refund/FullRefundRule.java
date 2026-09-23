package com.sumit.movieticketbookingsystem.booking.internal.refund;

import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundQuote;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundRule;

import java.time.Duration;
import java.util.List;

/** Every paisa back, fees included, whenever it's cancelled. Also what a cancelled show gets. */
public enum FullRefundRule implements RefundRule {
    INSTANCE;

    @Override
    public RefundQuote quote(List<BookingSeat> seats, Duration beforeShow) {
        return RefundRule.refund(seats, 100, true);
    }
}
