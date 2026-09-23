package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

/**
 * @param cancellationId the cancellation being refunded; null for a late-payment refund
 */
public record RefundRequest(UUID bookingId, UUID cancellationId, long amountPaise, RefundReason reason) {
}
