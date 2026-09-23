package com.sumit.movieticketbookingsystem.shared.user;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

class ForbiddenException extends DomainException {

    ForbiddenException() {
        super(ErrorCode.FORBIDDEN, "Admin access required");
    }
}
