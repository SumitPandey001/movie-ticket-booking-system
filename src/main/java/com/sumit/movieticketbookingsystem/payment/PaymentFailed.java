package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

/** A payment that was pending was declined. */
public record PaymentFailed(UUID paymentId, UUID bookingId, String reason) {
}
