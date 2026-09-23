package com.sumit.movieticketbookingsystem.payment.internal;

/**
 * What the gateway said: done (with its reference), declined (with a reason), or pending.
 */
record ProcessorResult(Outcome outcome, String reference, String failureReason) {

    enum Outcome {
        SUCCEEDED,
        FAILED,
        PENDING
    }

    static ProcessorResult success(String reference) {
        return new ProcessorResult(Outcome.SUCCEEDED, reference, null);
    }

    static ProcessorResult failure(String reason) {
        return new ProcessorResult(Outcome.FAILED, null, reason);
    }

    static ProcessorResult pending() {
        return new ProcessorResult(Outcome.PENDING, null, null);
    }
}
