package com.sumit.movieticketbookingsystem.shared.idempotency;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

import java.util.Map;

/**
 * The saved business error of the first attempt, raised again for a retry so it gets the same response.
 */
class ReplayedErrorException extends DomainException {

    private final Map<String, Object> details;

    ReplayedErrorException(SavedError error) {
        super(ErrorCode.valueOf(error.code()), error.detail());
        this.details = error.details();
    }

    @Override
    public Map<String, Object> details() {
        return details;
    }

    record SavedError(String code, String detail, Map<String, Object> details) {

        static SavedError of(DomainException e) {
            return new SavedError(e.code().name(), e.getMessage(), e.details());
        }
    }
}
