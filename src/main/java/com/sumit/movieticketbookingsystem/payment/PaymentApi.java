package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

/**
 * Taking the customer's money for a booking. A payment is started inside the caller's transaction and charged
 * outside any transaction, so no database locks are held while the gateway is working.
 */
public interface PaymentApi {

    /**
     * Checks the payment details and records an INITIATED payment. Joins the caller's transaction.
     *
     * @throws com.sumit.movieticketbookingsystem.shared.error.ValidationException for details that can't be charged
     */
    UUID initiate(InitiatePayment payment);

    /**
     * Charges the payment through the gateway and records the outcome. Call it with no transaction open.
     * A declined payment is a normal result, not an exception.
     */
    PaymentResult execute(UUID paymentId, PaymentDetails details, SimulatedOutcome outcome);

    /**
     * Refunds part or all of the booking's successful payment. Joins the caller's transaction; the money moves
     * after commit, and a {@link RefundCompleted} event follows. A booking's late-payment refund is only ever
     * requested once; asking again returns the existing one.
     *
     * @throws IllegalStateException if the booking has no successful payment or the refund would exceed it
     */
    UUID requestRefund(RefundRequest request);
}
