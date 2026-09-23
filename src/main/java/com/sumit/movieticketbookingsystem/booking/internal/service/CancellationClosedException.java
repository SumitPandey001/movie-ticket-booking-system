package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

import java.time.Duration;

class CancellationClosedException extends DomainException {

    CancellationClosedException(Duration cutoff) {
        super(ErrorCode.CANCELLATION_CLOSED,
                "Bookings can't be cancelled in the last " + cutoff.toMinutes() + " minutes before the show");
    }
}
