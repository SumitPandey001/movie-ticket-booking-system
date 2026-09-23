package com.sumit.movieticketbookingsystem.shared.error;

/**
 * Business-rule validation that can't be expressed with Bean Validation annotations,
 * e.g. "more seats than allowed per booking".
 */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
