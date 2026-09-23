package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

class ShowOverlapException extends DomainException {

    ShowOverlapException(long screenId) {
        super(ErrorCode.SHOW_OVERLAP,
                "Screen " + screenId + " already has a show in that slot (including the cleaning buffer)");
    }
}
