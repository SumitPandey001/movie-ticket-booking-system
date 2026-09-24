package com.sumit.movieticketbookingsystem.booking.internal.domain;

import com.sumit.movieticketbookingsystem.booking.CancellationReason;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

/** Some or all seats of a booking given back, and what that refunds. Created only by Booking.cancel. */
@Entity
public class Cancellation {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private CancellationReason reason;

    private int seatCount;

    private int refundPercent;

    private long refundPaise;

    private Instant createdAt;

    protected Cancellation() {
    }

    Cancellation(UUID id, CancellationReason reason, int seatCount, RefundQuote quote, Instant now) {
        this.id = id;
        this.reason = reason;
        this.seatCount = seatCount;
        this.refundPercent = quote.refundPercent();
        this.refundPaise = quote.refundPaise();
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public CancellationReason getReason() {
        return reason;
    }

    public int getSeatCount() {
        return seatCount;
    }

    public int getRefundPercent() {
        return refundPercent;
    }

    public long getRefundPaise() {
        return refundPaise;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
