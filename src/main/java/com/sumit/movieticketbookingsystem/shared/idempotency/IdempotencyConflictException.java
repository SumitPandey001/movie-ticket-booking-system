package com.sumit.movieticketbookingsystem.shared.idempotency;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

class IdempotencyConflictException extends DomainException {

    IdempotencyConflictException(ErrorCode code, String message) {
        super(code, message);
    }
}
