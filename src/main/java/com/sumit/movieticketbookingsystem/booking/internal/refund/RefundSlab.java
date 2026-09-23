package com.sumit.movieticketbookingsystem.booking.internal.refund;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Cancelling at least {@code minHoursBefore} hours before the show refunds {@code percent} of the ticket price. */
@Embeddable
public record RefundSlab(int minHoursBefore, @Column(name = "refund_percent") int percent) {
}
