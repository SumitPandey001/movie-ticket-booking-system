package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.booking.CancellationReason;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.booking.internal.domain.Cancellation;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundQuote;
import com.sumit.movieticketbookingsystem.booking.internal.domain.RefundRule;
import com.sumit.movieticketbookingsystem.booking.internal.persistence.BookingRepository;
import com.sumit.movieticketbookingsystem.booking.internal.refund.FullRefundRule;
import com.sumit.movieticketbookingsystem.inventory.InventoryApi;
import com.sumit.movieticketbookingsystem.payment.PaymentApi;
import com.sumit.movieticketbookingsystem.payment.RefundReason;
import com.sumit.movieticketbookingsystem.payment.RefundRequest;
import com.sumit.movieticketbookingsystem.pricing.CouponApi;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cancelling seats of a confirmed booking. The preview and the cancellation both use Booking.refundQuote, so
 * the amount previewed is the amount refunded.
 */
@Service
public class CancellationService {

    private static final Logger log = LoggerFactory.getLogger(CancellationService.class);

    private final BookingRepository bookings;
    private final InventoryApi inventory;
    private final PaymentApi payments;
    private final CouponApi coupons;
    private final BookingEvents events;
    private final Duration cancellationCutoff;
    private final Clock clock;

    CancellationService(BookingRepository bookings, InventoryApi inventory, PaymentApi payments, CouponApi coupons,
            BookingEvents events, BookingProperties properties, Clock clock) {
        this.bookings = bookings;
        this.inventory = inventory;
        this.payments = payments;
        this.coupons = coupons;
        this.events = events;
        this.cancellationCutoff = properties.cancellationCutoff();
        this.clock = clock;
    }

    // refundId is null when this cancellation refunds nothing
    public record CancellationResult(Booking booking, Cancellation cancellation, UUID refundId) {
    }

    // no seatIds means every active seat
    @Transactional(readOnly = true)
    public RefundQuote quote(UUID bookingId, UUID userId, Set<Long> seatIds) {
        Booking booking = bookings.findOwn(bookingId, userId);
        Instant now = Instant.now(clock);
        requireBeforeCutoff(booking, now);
        return booking.refundQuote(seatIds, ruleFor(booking, CancellationReason.CUSTOMER), now);
    }

    // no seatIds means every active seat
    @Transactional
    public CancellationResult cancel(UUID bookingId, UUID userId, Set<Long> seatIds) {
        Booking booking = bookings.findOwnForUpdate(bookingId, userId);
        Instant now = Instant.now(clock);
        requireBeforeCutoff(booking, now);
        return cancel(booking, seatIds, CancellationReason.CUSTOMER, now);
    }

    /**
     * One booking of a cancelled show, in its own transaction: a hold is released, a confirmed booking is
     * cancelled with a full refund (no policy, no cutoff). Anything else is left alone: a finished
     * booking needs nothing, and a pending payment takes the late-payment refund once it completes, because
     * confirm finds the show cancelled. The row lock orders this against a confirm already under way.
     */
    @Transactional
    public void cancelForShow(UUID bookingId) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow();
        Instant now = Instant.now(clock);
        switch (booking.getStatus()) {
            case HELD -> {
                booking.release(now);
                inventory.releaseHeld(booking.getShowId(), bookingId);
                coupons.release(bookingId);
            }
            case CONFIRMED -> cancel(booking, Set.of(), CancellationReason.SHOW_CANCELLED, now);
            default -> {
            }
        }
    }

    private CancellationResult cancel(Booking booking, Set<Long> seatIds, CancellationReason reason, Instant now) {
        Cancellation cancellation = booking.cancel(seatIds, ruleFor(booking, reason), reason, now);
        Set<Long> cancelledSeats = booking.getSeats().stream()
                .filter(seat -> cancellation.getId().equals(seat.cancellationId()))
                .map(BookingSeat::layoutSeatId)
                .collect(Collectors.toSet());
        inventory.releaseBooked(booking.getShowId(), cancelledSeats, booking.getId());

        UUID refundId = null;
        if (cancellation.getRefundPaise() > 0) {
            refundId = payments.requestRefund(new RefundRequest(booking.getId(), cancellation.getId(),
                    cancellation.getRefundPaise(), refundReason(reason)));
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            coupons.release(booking.getId());       // the coupon use comes back only once nothing is left of it
        }
        events.cancelled(booking, cancellation);
        log.info("Booking {}: cancelled {} ({}), refund {}", booking.getBookingRef(),
                booking.getSeats().stream().filter(seat -> cancellation.getId().equals(seat.cancellationId()))
                        .map(BookingSeat::seatLabel).collect(Collectors.joining(", ")),
                reason, Money.ofPaise(cancellation.getRefundPaise()).inRupees());
        return new CancellationResult(booking, cancellation, refundId);
    }

    // checked only for a confirmed booking, so cancelling one that's already cancelled says so instead
    private void requireBeforeCutoff(Booking booking, Instant now) {
        if (booking.getStatus() == BookingStatus.CONFIRMED
                && !now.isBefore(booking.getShowStartTime().minus(cancellationCutoff))) {
            throw new CancellationClosedException(cancellationCutoff);
        }
    }

    private static RefundRule ruleFor(Booking booking, CancellationReason reason) {
        return switch (reason) {
            case CUSTOMER -> booking.getRefundPolicySnapshot().refundRule();
            case SHOW_CANCELLED -> FullRefundRule.INSTANCE;             // not the customer's doing
        };
    }

    private static RefundReason refundReason(CancellationReason reason) {
        return switch (reason) {
            case CUSTOMER -> RefundReason.CUSTOMER;
            case SHOW_CANCELLED -> RefundReason.SHOW_CANCELLED;
        };
    }
}
