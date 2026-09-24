package com.sumit.movieticketbookingsystem.payment;

import java.util.UUID;

// cancellationId is null for a late-payment refund
public record RefundRequest(UUID bookingId, UUID cancellationId, long amountPaise, RefundReason reason) {
}
