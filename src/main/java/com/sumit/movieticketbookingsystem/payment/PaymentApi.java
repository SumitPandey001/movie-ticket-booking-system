package com.sumit.movieticketbookingsystem.payment;

import java.util.Optional;
import java.util.UUID;

/**
 * Taking the customer's money for a booking. A payment is started inside the caller's transaction and charged
 * outside any transaction, so no database locks are held while the gateway is working.
 */
public interface PaymentApi {

    /**
     * Checks the payment details and records an INITIATED payment, in the caller's transaction. Details that
     * can't be charged are rejected with a ValidationException.
     */
    UUID initiate(InitiatePayment payment);

    /**
     * Charges the payment through the gateway and records the outcome. Call it with no transaction open.
     * A declined payment is a normal result, not an exception.
     */
    PaymentResult execute(UUID paymentId, PaymentDetails details, SimulatedOutcome outcome);

    /**
     * Refunds part or all of the booking's successful payment, in the caller's transaction; the money moves after
     * commit and a RefundCompleted event follows. A booking's late-payment refund is only ever requested once:
     * asking again returns the existing one. Throws IllegalStateException if there's no successful payment or the
     * refund would be more than was paid.
     */
    UUID requestRefund(RefundRequest request);

    /** The booking's successful payment with its refunds; empty if it was never paid for. */
    Optional<PaymentSummary> summary(UUID bookingId);
}
