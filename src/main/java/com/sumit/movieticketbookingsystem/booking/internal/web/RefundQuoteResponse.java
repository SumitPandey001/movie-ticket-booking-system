package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundQuote;

import java.util.List;

/** @param retainedPaise what the customer won't get back */
record RefundQuoteResponse(List<String> seats, int refundPercent, long refundPaise, long retainedPaise) {

    static RefundQuoteResponse from(RefundQuote quote) {
        return new RefundQuoteResponse(quote.seats().stream().map(BookingSeat::seatLabel).toList(),
                quote.refundPercent(), quote.refundPaise(), quote.retainedPaise());
    }
}
