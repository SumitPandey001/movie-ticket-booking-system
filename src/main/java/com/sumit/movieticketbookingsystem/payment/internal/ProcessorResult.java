package com.sumit.movieticketbookingsystem.payment.internal;

/**
 * What the gateway said. Exactly one of the two strings is set.
 */
record ProcessorResult(String providerTxnId, String failureReason) {

    static ProcessorResult success(String providerTxnId) {
        return new ProcessorResult(providerTxnId, null);
    }

    static ProcessorResult failure(String reason) {
        return new ProcessorResult(null, reason);
    }

    boolean succeeded() {
        return providerTxnId != null;
    }
}
