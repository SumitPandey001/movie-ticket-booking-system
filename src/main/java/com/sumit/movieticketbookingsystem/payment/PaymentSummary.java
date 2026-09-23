package com.sumit.movieticketbookingsystem.payment;

import java.util.List;
import java.util.UUID;

/**
 * What a booking was paid with and what has gone back, for the booking details page.
 *
 * @param maskedDetails e.g. '•••• 4242' or 'asha@okbank'; never the full details
 * @param refunds       oldest first
 */
public record PaymentSummary(UUID paymentId, PaymentMethod method, String maskedDetails, long amountPaise,
                             List<Refund> refunds) {

    /** @param cancellationId the cancellation this refund is for; null for a late-payment refund */
    public record Refund(UUID refundId, UUID cancellationId, long amountPaise, RefundReason reason, Status status) {

        public enum Status {
            INITIATED,
            COMPLETED,
            FAILED
        }
    }
}
