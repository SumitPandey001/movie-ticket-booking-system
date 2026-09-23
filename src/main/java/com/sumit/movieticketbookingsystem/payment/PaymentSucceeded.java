package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

/** A payment that was pending went through. */
public record PaymentSucceeded(UUID paymentId, UUID bookingId) {
}
