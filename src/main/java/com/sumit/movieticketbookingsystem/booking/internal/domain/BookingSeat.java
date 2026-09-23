package com.sumit.movieticketbookingsystem.booking.internal.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.UUID;

/**
 * One seat of a booking with the price it was held at. Amounts are in paise.
 *
 * @param feePaise       convenience fee plus the GST on it
 * @param amountPaise    everything this seat costs: base − discount + fee + ticket GST
 * @param cancellationId the cancellation that covered this seat, or null while it's active
 */
@Embeddable
public record BookingSeat(long layoutSeatId, String seatLabel, long categoryId, long basePaise,
                          long discountPaise, long feePaise, long amountPaise,
                          @Enumerated(EnumType.STRING) Status status, UUID cancellationId) {

    public enum Status {
        ACTIVE,
        CANCELLED
    }

    public BookingSeat {
        if (amountPaise < feePaise) {
            throw new IllegalArgumentException("Seat " + seatLabel + " costs less than its fee");
        }
    }

    /** A seat as it's first held. */
    public BookingSeat(long layoutSeatId, String seatLabel, long categoryId, long basePaise, long discountPaise,
            long feePaise, long amountPaise) {
        this(layoutSeatId, seatLabel, categoryId, basePaise, discountPaise, feePaise, amountPaise, Status.ACTIVE,
                null);
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** The ticket part of the amount: what a percentage refund applies to. */
    public long ticketPaise() {
        return amountPaise - feePaise;
    }

    BookingSeat cancelledBy(UUID cancellation) {
        return new BookingSeat(layoutSeatId, seatLabel, categoryId, basePaise, discountPaise, feePaise, amountPaise,
                Status.CANCELLED, cancellation);
    }
}
