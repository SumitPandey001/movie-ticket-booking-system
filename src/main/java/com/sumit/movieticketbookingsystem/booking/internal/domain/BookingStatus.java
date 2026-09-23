package com.sumit.movieticketbookingsystem.booking.internal.domain;

import com.sumit.movieticketbookingsystem.shared.error.IllegalTransitionException;

import java.util.EnumSet;
import java.util.Set;

/**
 * <pre>
 * HELD ──► PAYMENT_PENDING ──► CONFIRMED ──► CANCELLED
 *   │            ├──► FAILED
 *   │            └──► EXPIRED   (seats lost while paying; refunded)
 *   ├──► RELEASED               (customer let go, or show cancelled)
 *   └──► EXPIRED                (hold time ran out)
 * </pre>
 * Cancelling only some seats keeps a booking CONFIRMED. Refund progress lives on the refund, not here.
 */
public enum BookingStatus {

    HELD {
        @Override
        Set<BookingStatus> next() {
            return EnumSet.of(PAYMENT_PENDING, RELEASED, EXPIRED);
        }
    },
    PAYMENT_PENDING {
        @Override
        Set<BookingStatus> next() {
            return EnumSet.of(CONFIRMED, FAILED, EXPIRED);
        }
    },
    CONFIRMED {
        @Override
        Set<BookingStatus> next() {
            return EnumSet.of(CANCELLED);
        }
    },
    CANCELLED,
    RELEASED,
    EXPIRED,
    FAILED;

    Set<BookingStatus> next() {
        return EnumSet.noneOf(BookingStatus.class);
    }

    /** HELD and PAYMENT_PENDING bookings own their seats in inventory. */
    public boolean holdsSeats() {
        return this == HELD || this == PAYMENT_PENDING;
    }

    BookingStatus transitionTo(BookingStatus target) {
        if (!next().contains(target)) {
            throw new IllegalTransitionException("Booking", this, target);
        }
        return target;
    }
}
