package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

class ShowNotBookableException extends DomainException {

    ShowNotBookableException(long showId) {
        super(ErrorCode.SHOW_NOT_BOOKABLE, "Show " + showId + " isn't open for booking");
    }
}
