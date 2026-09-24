package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

// providerTxnId is set on success, failureReason on failure (worded for the customer)
public record PaymentResult(UUID paymentId, Status status, String providerTxnId, String failureReason) {

    public enum Status {
        SUCCESS,
        FAILED,
        /** the outcome comes later as a PaymentSucceeded or PaymentFailed event */
        PENDING
    }
}
