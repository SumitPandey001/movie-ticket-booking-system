package com.sumit.movieticketbookingsystem.payment;

import java.util.List;
import java.util.UUID;

/**
 * What a booking was paid with and what has gone back, for the booking details page. maskedDetails is
 * something like '•••• 4242' or the UPI id, never the full details. Refunds are oldest first.
 */
public record PaymentSummary(UUID paymentId, PaymentMethod method, String maskedDetails, long amountPaise,
                             List<Refund> refunds) {

    // cancellationId is null for a late-payment refund
    public record Refund(UUID refundId, UUID cancellationId, long amountPaise, RefundReason reason, Status status) {

        public enum Status {
            INITIATED,
            COMPLETED,
            FAILED
        }
    }
}
