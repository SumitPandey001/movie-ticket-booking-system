package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.booking.internal.domain.PriceTotals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record BookingResponse(UUID bookingId, String bookingRef, BookingStatus status, long showId, Instant showStartTime,
                       Instant holdExpiresAt, List<Seat> seats, Price price, String coupon) {

    static BookingResponse from(Booking booking) {
        PriceTotals totals = booking.getTotals();
        return new BookingResponse(booking.getId(), booking.getBookingRef(), booking.getStatus(), booking.getShowId(),
                booking.getShowStartTime(), booking.getHoldExpiresAt(),
                booking.getSeats().stream()
                        .map(seat -> new Seat(seat.layoutSeatId(), seat.seatLabel(), seat.amountPaise()))
                        .toList(),
                new Price(totals.subtotal(), totals.discount(), totals.fee(), totals.tax(), totals.total()),
                booking.getCouponCode());
    }

    record Seat(long seatId, String label, long amountPaise) {
    }

    record Price(long subtotalPaise, long discountPaise, long feePaise, long taxPaise, long totalPaise) {
    }
}
