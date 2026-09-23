package com.sumit.movieticketbookingsystem.booking.internal.listener;

import com.sumit.movieticketbookingsystem.booking.internal.service.CheckoutService;
import com.sumit.movieticketbookingsystem.payment.PaymentFailed;
import com.sumit.movieticketbookingsystem.payment.PaymentSucceeded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

/**
 * Payments that finish later land here and take the same path as an immediate answer. No surrounding transaction:
 * confirming and refunding are separate transactions in {@link CheckoutService}, and a failed confirm must not drag
 * the refund down with it.
 */
@Component
class PaymentEventsListener {

    private final CheckoutService checkout;

    PaymentEventsListener(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(PaymentSucceeded event) {
        checkout.completePayment(event.bookingId(), true);
    }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    void on(PaymentFailed event) {
        checkout.completePayment(event.bookingId(), false);
    }
}
