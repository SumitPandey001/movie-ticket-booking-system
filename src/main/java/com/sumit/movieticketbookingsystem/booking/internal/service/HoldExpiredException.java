package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

import java.util.UUID;

class HoldExpiredException extends DomainException {

    HoldExpiredException(UUID bookingId) {
        super(ErrorCode.HOLD_EXPIRED, "The hold on booking " + bookingId + " has run out; please pick seats again");
    }
}
