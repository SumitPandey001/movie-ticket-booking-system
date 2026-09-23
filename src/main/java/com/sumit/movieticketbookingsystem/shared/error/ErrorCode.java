package com.sumit.movieticketbookingsystem.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes returned to clients in the {@code code} field of every error response.
 * Clients branch on these, so never rename one once it has shipped.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    ALREADY_EXISTS(HttpStatus.CONFLICT, "Already exists"),
    INVALID_STATE(HttpStatus.CONFLICT, "Invalid state"),
    SHOW_OVERLAP(HttpStatus.CONFLICT, "Show overlaps another show"),
    SEATS_UNAVAILABLE(HttpStatus.CONFLICT, "Seats unavailable"),
    ACTIVE_HOLD_EXISTS(HttpStatus.CONFLICT, "Active hold exists"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key reused"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "Request still in progress"),
    COUPON_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "Coupon can't be used"),
    SHOW_NOT_BOOKABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Show not bookable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
