package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

/**
 * The money is back with the customer. Carries what a notification needs, so nobody has to call back.
 *
 * @param reference the booking reference
 */
public record RefundCompleted(UUID refundId, UUID bookingId, UUID customerId, String reference, long amountPaise,
                              RefundReason reason) {
}
