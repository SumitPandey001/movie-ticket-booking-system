package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

import java.util.Map;
import java.util.UUID;

class ActiveHoldExistsException extends DomainException {

    private final Map<String, Object> details;

    ActiveHoldExistsException(UUID bookingId) {
        this(Map.of("bookingId", bookingId));
    }

    // a parallel request has just created the hold, so its id isn't known yet
    ActiveHoldExistsException() {
        this(Map.of());
    }

    private ActiveHoldExistsException(Map<String, Object> details) {
        super(ErrorCode.ACTIVE_HOLD_EXISTS, "You already have seats on hold for this show");
        this.details = details;
    }

    @Override
    public Map<String, Object> details() {
        return details;
    }
}
