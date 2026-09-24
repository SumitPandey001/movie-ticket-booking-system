package com.sumit.movieticketbookingsystem.booking.internal.domain;

import com.sumit.movieticketbookingsystem.shared.Money;

import java.time.Duration;
import java.util.List;

/**
 * How much a cancellation refunds. One implementation per kind of refund policy; a booking gets its rule from
 * its RefundPolicySnapshot.
 */
public interface RefundRule {

    RefundQuote quote(List<BookingSeat> seats, Duration beforeShow);

    /** Refunds percent of the ticket price, plus the fees when refundFees is set. */
    static RefundQuote refund(List<BookingSeat> seats, int percent, boolean refundFees) {
        long ticket = seats.stream().mapToLong(BookingSeat::ticketPaise).sum();
        long fees = seats.stream().mapToLong(BookingSeat::feePaise).sum();
        long refund = Money.ofPaise(ticket).percent(percent).paise()
                + (refundFees ? fees : 0);
        return new RefundQuote(seats, percent, refund, ticket + fees - refund);
    }
}
