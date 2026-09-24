package com.sumit.movieticketbookingsystem.booking.internal.domain;

import com.sumit.movieticketbookingsystem.booking.CancellationReason;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicySnapshot;
import com.sumit.movieticketbookingsystem.shared.error.InvalidStateException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A customer's claim on some seats of one show, from the hold through payment to cancellation.
 * Status only changes through the methods here, which go through BookingStatus.
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

    private String couponCode;

    @JdbcTypeCode(SqlTypes.JSON)
    private RefundPolicySnapshot refundPolicySnapshot;

    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "booking_seat", joinColumns = @JoinColumn(name = "booking_id"))
    private Set<BookingSeat> seats = new HashSet<>();

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    @OrderBy("createdAt")
    private List<Cancellation> cancellations = new ArrayList<>();

    // A wrapper type on purpose: null tells Spring Data the booking is new despite its assigned id,
    // so save() inserts instead of selecting first.
    @Version
    private Long version;

    private Instant createdAt;

    private Instant confirmedAt;

    private Instant reminderSentAt;

    private Instant closedAt;

    protected Booking() {
    }

    // couponCode is the coupon the price includes, or null
    public static Booking hold(UUID id, String bookingRef, UUID userId, long showId, Instant showStartTime,
            Instant holdExpiresAt, List<BookingSeat> seats, PriceTotals totals, String couponCode, Instant now) {
        if (seats.isEmpty()) {
            throw new IllegalArgumentException("A booking needs at least one seat");
        }
        requireSeatsAddUp(seats, totals);

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
        booking.setPrice(totals, couponCode);
        booking.createdAt = now;
        return booking;
    }

    /**
     * Replaces the frozen price after the customer changes the coupon on a live hold. Same seats, new amounts.
     */
    public void reprice(List<BookingSeat> newSeats, PriceTotals totals, String newCouponCode) {
        if (status != BookingStatus.HELD) {
            throw new InvalidStateException("Only a held booking can change its coupon; this one is " + status);
        }
        Set<Long> current = seats.stream().map(BookingSeat::layoutSeatId).collect(Collectors.toSet());
        Set<Long> replacement = newSeats.stream().map(BookingSeat::layoutSeatId).collect(Collectors.toSet());
        if (!current.equals(replacement)) {
            throw new IllegalArgumentException("A new price must cover exactly the booking's seats");
        }
        requireSeatsAddUp(newSeats, totals);
        seats.clear();
        seats.addAll(newSeats);
        setPrice(totals, newCouponCode);
    }

    /**
     * The customer starts paying. The seats stay reserved for at least paymentWindow from now, so a
     * payment started just before the hold runs out still has time to finish; an existing longer hold isn't cut.
     */
    public void startPayment(Instant now, Duration paymentWindow) {
        status = status.transitionTo(BookingStatus.PAYMENT_PENDING);
        Instant paymentDeadline = now.plus(paymentWindow);
        if (paymentDeadline.isAfter(holdExpiresAt)) {
            holdExpiresAt = paymentDeadline;
        }
    }

    // the policy is copied onto the booking, so editing it later doesn't change this sale
    public void confirm(RefundPolicySnapshot refundPolicy, Instant now) {
        status = status.transitionTo(BookingStatus.CONFIRMED);
        refundPolicySnapshot = refundPolicy;
        confirmedAt = now;
    }

    /** The payment was declined; the customer starts again with a new hold. */
    public void fail(Instant now) {
        close(BookingStatus.FAILED, now);
    }

    /** The customer lets the hold go before it runs out. */
    public void release(Instant now) {
        close(BookingStatus.RELEASED, now);
    }

    /** The hold ran out; the seats may already belong to someone else. */
    public void expire(Instant now) {
        close(BookingStatus.EXPIRED, now);
    }

    /**
     * What cancelling the seats now would refund, without cancelling anything. No seatIds means every active seat.
     */
    public RefundQuote refundQuote(Set<Long> seatIds, RefundRule rule, Instant now) {
        if (status != BookingStatus.CONFIRMED) {
            throw new InvalidStateException("Only a confirmed booking can be cancelled; this one is " + status);
        }
        return rule.quote(seatsToCancel(seatIds), Duration.between(now, showStartTime));
    }

    /**
     * Gives the seats back. The booking stays CONFIRMED while any seat is still active, and is CANCELLED once
     * none are. Refund, inventory and coupon are the caller's job; this only records what was cancelled.
     */
    public Cancellation cancel(Set<Long> seatIds, RefundRule rule, CancellationReason reason, Instant now) {
        RefundQuote quote = refundQuote(seatIds, rule, now);
        Cancellation cancellation = new Cancellation(UUID.randomUUID(), reason, quote.seats().size(), quote, now);
        cancellations.add(cancellation);
        for (BookingSeat seat : quote.seats()) {
            seats.remove(seat);
            seats.add(seat.cancelledBy(cancellation.getId()));
        }
        if (seats.stream().noneMatch(BookingSeat::isActive)) {
            close(BookingStatus.CANCELLED, now);
        }
        return cancellation;
    }

    private List<BookingSeat> seatsToCancel(Set<Long> seatIds) {
        List<BookingSeat> active = getSeats().stream().filter(BookingSeat::isActive).toList();
        if (seatIds.isEmpty()) {
            return active;
        }
        for (long seatId : seatIds) {
            BookingSeat seat = seats.stream().filter(s -> s.layoutSeatId() == seatId).findFirst()
                    .orElseThrow(() -> new ValidationException("Seat " + seatId + " isn't part of this booking"));
            if (!seat.isActive()) {
                throw new InvalidStateException("Seat " + seat.seatLabel() + " is already cancelled");
            }
        }
        return active.stream().filter(seat -> seatIds.contains(seat.layoutSeatId())).toList();
    }

    /**
     * Whether the sweeper should close this booking: a hold past its time, or a payment that's had its window
     * and the grace period on top without an answer.
     */
    public boolean isDueToExpire(Instant now, Duration paymentGrace) {
        return switch (status) {
            case HELD -> !now.isBefore(holdExpiresAt);
            case PAYMENT_PENDING -> !now.isBefore(holdExpiresAt.plus(paymentGrace));
            default -> false;
        };
    }

    /** Whether a reminder should go out now: confirmed, not reminded yet, and the show within the lead time. */
    public boolean isDueForReminder(Instant now, Duration leadTime) {
        return status == BookingStatus.CONFIRMED && reminderSentAt == null
                && showStartTime.isAfter(now) && !showStartTime.isAfter(now.plus(leadTime));
    }

    public void markReminded(Instant now) {
        if (reminderSentAt != null) {
            throw new InvalidStateException("Booking " + bookingRef + " has already been reminded");
        }
        reminderSentAt = now;
    }

    public boolean isHoldExpired(Instant now) {
        return status == BookingStatus.HELD && !now.isBefore(holdExpiresAt);
    }

    private void setPrice(PriceTotals totals, String coupon) {
        subtotalPaise = totals.subtotal();
        discountPaise = totals.discount();
        feePaise = totals.fee();
        taxPaise = totals.tax();
        totalPaise = totals.total();
        couponCode = coupon;
    }

    private static void requireSeatsAddUp(List<BookingSeat> seats, PriceTotals totals) {
        long seatTotal = seats.stream().mapToLong(BookingSeat::amountPaise).sum();
        if (seatTotal != totals.total()) {
            throw new IllegalArgumentException("Seat amounts add up to " + seatTotal + ", not " + totals.total());
        }
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

    public String getCouponCode() {
        return couponCode;
    }

    public PriceTotals getTotals() {
        return new PriceTotals(subtotalPaise, discountPaise, feePaise, taxPaise, totalPaise);
    }

    /** Seats in label order (A1, A2, B1 ...). */
    public List<BookingSeat> getSeats() {
        return seats.stream().sorted(Comparator.comparing(BookingSeat::seatLabel)).toList();
    }

    public List<Cancellation> getCancellations() {
        return List.copyOf(cancellations);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public RefundPolicySnapshot getRefundPolicySnapshot() {
        return refundPolicySnapshot;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getReminderSentAt() {
        return reminderSentAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
