package com.sumit.movieticketbookingsystem.shared.error;

import java.util.Map;

/**
 * Base for business errors that should reach the client as a 4xx with a specific ErrorCode.
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

    /** Extra fields for the error response, e.g. which seats were taken. None by default. */
    public Map<String, Object> details() {
        return Map.of();
    }
}
