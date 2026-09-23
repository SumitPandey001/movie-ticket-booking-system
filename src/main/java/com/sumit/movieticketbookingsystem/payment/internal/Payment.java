package com.sumit.movieticketbookingsystem.payment.internal;

import com.sumit.movieticketbookingsystem.payment.PaymentMethod;
import com.sumit.movieticketbookingsystem.shared.error.InvalidStateException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
class Payment {

    @Id
    private UUID id;

    private UUID bookingId;

    private UUID customerId;

    private String reference;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    private long amountPaise;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String providerTxnId;

    private String maskedDetails;

    private String failureReason;

    // wrapper type so Spring Data sees a new payment despite its assigned id
    @Version
    private Long version;

    private Instant createdAt;

    private Instant completedAt;

    protected Payment() {
    }

    Payment(UUID id, UUID bookingId, UUID customerId, String reference, PaymentMethod method, long amountPaise,
            String maskedDetails, Instant now) {
        this.id = id;
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.reference = reference;
        this.method = method;
        this.amountPaise = amountPaise;
        this.maskedDetails = maskedDetails;
        this.status = PaymentStatus.INITIATED;
        this.createdAt = now;
    }

    void succeed(String providerTxnId, Instant now) {
        complete(PaymentStatus.SUCCESS, now);
        this.providerTxnId = providerTxnId;
    }

    void fail(String reason, Instant now) {
        complete(PaymentStatus.FAILED, now);
        this.failureReason = reason;
    }

    private void complete(PaymentStatus outcome, Instant now) {
        if (status != PaymentStatus.INITIATED) {
            throw new InvalidStateException("Payment " + id + " is already " + status);
        }
        status = outcome;
        completedAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getBookingId() {
        return bookingId;
    }

    UUID getCustomerId() {
        return customerId;
    }

    PaymentMethod getMethod() {
        return method;
    }

    long getAmountPaise() {
        return amountPaise;
    }

    String getReference() {
        return reference;
    }

    PaymentStatus getStatus() {
        return status;
    }

    String getProviderTxnId() {
        return providerTxnId;
    }

    String getFailureReason() {
        return failureReason;
    }
}
