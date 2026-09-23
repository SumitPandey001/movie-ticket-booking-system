package com.sumit.movieticketbookingsystem.booking.internal.domain;

import java.util.List;

/**
 * What cancelling these seats now would give back.
 *
 * @param refundPercent share of the ticket price refunded; the convenience fee comes back only if the policy says so
 * @param retainedPaise what the customer doesn't get back
 */
public record RefundQuote(List<BookingSeat> seats, int refundPercent, long refundPaise, long retainedPaise) {
}
