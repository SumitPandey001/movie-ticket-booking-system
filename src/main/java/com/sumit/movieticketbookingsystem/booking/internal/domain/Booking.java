package com.sumit.movieticketbookingsystem.booking.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A customer's claim on some seats of one show, from the hold through payment to cancellation.
 * Status only changes through the methods here, which go through {@link BookingStatus}.
 */
@Entity
public class Booking {

    @Id
    private UUID id;

    private String bookingRef;

    private UUID userId;

    private Long showId;

    private Instant showStartTime;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private Instant holdExpiresAt;

    private int seatCount;

    private long subtotalPaise;

    private long discountPaise;

    private long feePaise;

    private long taxPaise;

    private long totalPaise;

    @ElementCollection
    @CollectionTable(name = "booking_seat", joinColumns = @JoinColumn(name = "booking_id"))
    private Set<BookingSeat> seats = new HashSet<>();

    // A wrapper type on purpose: null tells Spring Data the booking is new despite its assigned id,
    // so save() inserts instead of selecting first.
    @Version
    private Long version;

    private Instant createdAt;

    private Instant closedAt;

    protected Booking() {
    }

    public static Booking hold(UUID id, String bookingRef, UUID userId, long showId, Instant showStartTime,
            Instant holdExpiresAt, List<BookingSeat> seats, PriceTotals totals, Instant now) {
        if (seats.isEmpty()) {
            throw new IllegalArgumentException("A booking needs at least one seat");
        }
        long seatTotal = seats.stream().mapToLong(BookingSeat::amountPaise).sum();
        if (seatTotal != totals.total()) {
            throw new IllegalArgumentException("Seat amounts add up to " + seatTotal + ", not " + totals.total());
        }

        Booking booking = new Booking();
        booking.id = id;
        booking.bookingRef = bookingRef;
        booking.userId = userId;
        booking.showId = showId;
        booking.showStartTime = showStartTime;
        booking.status = BookingStatus.HELD;
        booking.holdExpiresAt = holdExpiresAt;
        booking.seats.addAll(seats);
        booking.seatCount = seats.size();
        booking.subtotalPaise = totals.subtotal();
        booking.discountPaise = totals.discount();
        booking.feePaise = totals.fee();
        booking.taxPaise = totals.tax();
        booking.totalPaise = totals.total();
        booking.createdAt = now;
        return booking;
    }

    /** The customer lets the hold go before it runs out. */
    public void release(Instant now) {
        close(BookingStatus.RELEASED, now);
    }

    /** The hold ran out; the seats may already belong to someone else. */
    public void expire(Instant now) {
        close(BookingStatus.EXPIRED, now);
    }

    public boolean isHoldExpired(Instant now) {
        return status == BookingStatus.HELD && !now.isBefore(holdExpiresAt);
    }

    private void close(BookingStatus terminal, Instant now) {
        status = status.transitionTo(terminal);
        closedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getShowId() {
        return showId;
    }

    public Instant getShowStartTime() {
        return showStartTime;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public PriceTotals getTotals() {
        return new PriceTotals(subtotalPaise, discountPaise, feePaise, taxPaise, totalPaise);
    }

    /** Seats in label order (A1, A2, B1 ...). */
    public List<BookingSeat> getSeats() {
        return seats.stream().sorted(Comparator.comparing(BookingSeat::seatLabel)).toList();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
