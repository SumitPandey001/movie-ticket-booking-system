package com.sumit.movieticketbookingsystem.shared.user;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

class UnauthenticatedException extends DomainException {

    UnauthenticatedException(String header) {
        super(ErrorCode.UNAUTHENTICATED, "Missing or invalid " + header + " header");
    }
}
