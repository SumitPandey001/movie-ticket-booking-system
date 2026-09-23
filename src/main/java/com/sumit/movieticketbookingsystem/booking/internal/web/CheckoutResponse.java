package com.sumit.movieticketbookingsystem.booking.internal.web;

import com.sumit.movieticketbookingsystem.booking.internal.service.CheckoutService.Checkout;
import com.sumit.movieticketbookingsystem.payment.PaymentResult;

import java.util.UUID;

/**
 * A declined payment is a normal answer here ({@code paymentStatus: FAILED}), not an error.
 */
record CheckoutResponse(UUID paymentId, PaymentResult.Status paymentStatus, String failureReason,
                        BookingResponse booking) {

    static CheckoutResponse from(Checkout checkout) {
        return new CheckoutResponse(checkout.payment().paymentId(), checkout.payment().status(),
                checkout.payment().failureReason(), BookingResponse.from(checkout.booking()));
    }
}
