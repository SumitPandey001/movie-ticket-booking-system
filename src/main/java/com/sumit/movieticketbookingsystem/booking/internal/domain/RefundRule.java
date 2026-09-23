package com.sumit.movieticketbookingsystem.booking.internal.domain;

import com.sumit.movieticketbookingsystem.shared.Money;

import java.time.Duration;
import java.util.List;

/**
 * How much a cancellation refunds. One implementation per kind of refund policy; a booking's comes from its
 * {@code RefundPolicySnapshot}. Adding a kind of policy means adding a rule, not editing the others.
 */
public interface RefundRule {

    /** @param beforeShow time left until the show starts */
    RefundQuote quote(List<BookingSeat> seats, Duration beforeShow);

    /** Refunds {@code percent} of the ticket price, plus the fees when {@code refundFees} is set. */
    static RefundQuote refund(List<BookingSeat> seats, int percent, boolean refundFees) {
        long ticket = seats.stream().mapToLong(BookingSeat::ticketPaise).sum();
        long fees = seats.stream().mapToLong(BookingSeat::feePaise).sum();
        long refund = Money.ofPaise(ticket).percent(percent).paise()
                + (refundFees ? fees : 0);
        return new RefundQuote(seats, percent, refund, ticket + fees - refund);
    }
}
