package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.RefundReason;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
class Refund {

    @Id
    private UUID id;

    private UUID paymentId;

    private UUID bookingId;

    private UUID cancellationId;

    private long amountPaise;

    @Enumerated(EnumType.STRING)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    private String providerRefundId;

    private Instant createdAt;

    private Instant completedAt;

    protected Refund() {
    }

    Refund(UUID id, UUID paymentId, UUID bookingId, UUID cancellationId, long amountPaise, RefundReason reason,
            Instant now) {
        this.id = id;
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.cancellationId = cancellationId;
        this.amountPaise = amountPaise;
        this.reason = reason;
        this.status = RefundStatus.INITIATED;
        this.createdAt = now;
    }

    void complete(String providerRefundId, Instant now) {
        this.status = RefundStatus.COMPLETED;
        this.providerRefundId = providerRefundId;
        this.completedAt = now;
    }

    void fail(Instant now) {
        this.status = RefundStatus.FAILED;
        this.completedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getPaymentId() {
        return paymentId;
    }

    UUID getBookingId() {
        return bookingId;
    }

    long getAmountPaise() {
        return amountPaise;
    }

    RefundReason getReason() {
        return reason;
    }

    RefundStatus getStatus() {
        return status;
    }
}
