package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

/**
 * @param providerTxnId set on success
 * @param failureReason set on failure, in words for the customer
 */
public record PaymentResult(UUID paymentId, Status status, String providerTxnId, String failureReason) {

    public enum Status {
        SUCCESS,
        FAILED,
        /** the outcome comes later as a {@link PaymentSucceeded} or {@link PaymentFailed} event */
        PENDING
    }
}
