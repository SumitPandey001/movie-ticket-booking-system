package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

/**
 * The money is back with the customer. Carries what a notification needs (reference is the booking
 * reference), so nobody has to call back.
 */
public record RefundCompleted(UUID refundId, UUID bookingId, UUID customerId, String reference, long amountPaise,
                              RefundReason reason) {
}
