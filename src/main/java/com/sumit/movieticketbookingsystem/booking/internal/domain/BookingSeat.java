package com.sumit.movieticketbookingsystem.booking.internal.domain;

import jakarta.persistence.Embeddable;

/**
 * One seat of a booking with the price it was held at. Amounts are in paise.
 *
 * @param feePaise    convenience fee plus the GST on it
 * @param amountPaise everything this seat costs: base − discount + fee + ticket GST
 */
@Embeddable
public record BookingSeat(long layoutSeatId, String seatLabel, long categoryId, long basePaise,
                          long discountPaise, long feePaise, long amountPaise) {

    public BookingSeat {
        if (amountPaise < feePaise) {
            throw new IllegalArgumentException("Seat " + seatLabel + " costs less than its fee");
        }
    }
}
