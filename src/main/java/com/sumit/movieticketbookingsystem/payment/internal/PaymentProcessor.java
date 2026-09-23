package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentMethod;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;

/**
 * One payment method (Strategy). A real gateway would be another implementation (Adapter) of this same interface.
 * Every processor answers with a result; none throws "unsupported".
 */
interface PaymentProcessor {

    PaymentMethod method();

    void validate(PaymentDetails details);

    /** A form safe to store and show back to the customer, e.g. "•••• 4242". */
    String mask(PaymentDetails details);

    ProcessorResult charge(Payment payment, SimulatedOutcome outcome);

    /** Every method supports refunds back to where the money came from. */
    ProcessorResult refund(Refund refund);
}
