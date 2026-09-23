package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.persistence.BookingRepository;
import com.sumit.movieticketbookingsystem.inventory.InventoryApi;
import com.sumit.movieticketbookingsystem.payment.InitiatePayment;
import com.sumit.movieticketbookingsystem.payment.PaymentApi;
import com.sumit.movieticketbookingsystem.payment.PaymentDetails;
import com.sumit.movieticketbookingsystem.payment.PaymentResult;
import com.sumit.movieticketbookingsystem.payment.SimulatedOutcome;
import com.sumit.movieticketbookingsystem.pricing.CouponApi;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Paying for a hold. Three steps, deliberately not one transaction: start the payment (tx1), call the gateway with
 * no transaction open so no locks are held while it works, then confirm or fail the booking (tx2).
 */
@Service
public class CheckoutService {

    private final BookingRepository bookings;
    private final PaymentApi payments;
    private final InventoryApi inventory;
    private final CouponApi coupons;
    private final TransactionTemplate tx;
    private final Duration paymentWindow;
    private final Clock clock;

    CheckoutService(BookingRepository bookings, PaymentApi payments, InventoryApi inventory, CouponApi coupons,
            TransactionTemplate tx, BookingProperties properties, Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.inventory = inventory;
        this.coupons = coupons;
        this.tx = tx;
        this.paymentWindow = properties.paymentWindow();
        this.clock = clock;
    }

    public record Checkout(Booking booking, PaymentResult payment) {
    }

    // TransactionTemplate rather than @Transactional: the steps are private and called from here, where
    // Spring's proxy-based @Transactional wouldn't apply.
    public Checkout pay(UUID bookingId, UUID userId, PaymentDetails details, SimulatedOutcome outcome) {
        UUID paymentId = tx.execute(status -> startPayment(bookingId, userId, details));
        PaymentResult result = payments.execute(paymentId, details, outcome);
        Booking booking = tx.execute(status -> result.status() == PaymentResult.Status.SUCCESS
                ? confirm(bookingId)
                : fail(bookingId));
        return new Checkout(booking, result);
    }

    /** Invalid payment details roll this back too, leaving the booking HELD so the customer can try again. */
    private UUID startPayment(UUID bookingId, UUID userId, PaymentDetails details) {
        Booking booking = bookings.findOwn(bookingId, userId);
        Instant now = Instant.now(clock);
        if (booking.isHoldExpired(now)) {
            throw new HoldExpiredException(bookingId);
        }
        booking.startPayment(now, paymentWindow);
        return payments.initiate(new InitiatePayment(bookingId, userId, booking.getBookingRef(),
                booking.getTotals().total(), details));
    }

    private Booking confirm(UUID bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow();
        Instant now = Instant.now(clock);
        inventory.confirm(booking.getShowId(),
                booking.getSeats().stream().map(BookingSeat::layoutSeatId).collect(Collectors.toSet()),
                bookingId, now);
        coupons.consume(bookingId);
        booking.confirm(now);
        return booking;
    }

    private Booking fail(UUID bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow();
        booking.fail(Instant.now(clock));
        inventory.releaseHeld(booking.getShowId(), bookingId);
        coupons.release(bookingId);
        return booking;
    }
}
