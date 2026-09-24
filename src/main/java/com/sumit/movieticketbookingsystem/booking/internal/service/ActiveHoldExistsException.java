package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

import java.util.Map;
import java.util.UUID;

class ActiveHoldExistsException extends DomainException {

    private final UUID bookingId;

    // bookingId is null when a parallel request has just created the hold and we don't know its id
    ActiveHoldExistsException(UUID bookingId) {
        super(ErrorCode.ACTIVE_HOLD_EXISTS, "You already have seats on hold for this show");
        this.bookingId = bookingId;
    }

    @Override
    public Map<String, Object> details() {
        return bookingId == null ? Map.of() : Map.of("bookingId", bookingId);
    }
}
