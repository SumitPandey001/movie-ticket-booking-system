package com.sumit.movieticketbookingsystem.shared.error;

/**
 * Base for business errors that should reach the client as a 4xx with a specific {@link ErrorCode}.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode code;

    protected DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
