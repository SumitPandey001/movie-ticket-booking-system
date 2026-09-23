package com.sumit.movieticketbookingsystem.booking.internal.domain;

import com.sumit.movieticketbookingsystem.booking.CancellationReason;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicySnapshot;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicyType;
import com.sumit.movieticketbookingsystem.shared.error.IllegalTransitionException;
import com.sumit.movieticketbookingsystem.shared.error.InvalidStateException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private static final Instant EXPIRES = NOW.plus(Duration.ofMinutes(8));

    // two regular seats at ₹200: fee ₹20 + 18% GST on each, see the pricing worked example
    private static final List<BookingSeat> SEATS = List.of(
            new BookingSeat(12, "F8", 1, 20000, 0, 2360, 25960),
            new BookingSeat(11, "F7", 1, 20000, 0, 2360, 25960));
    private static final PriceTotals TOTALS = new PriceTotals(40000, 0, 4000, 7920, 51920);
    private static final RefundRule FULL_REFUND = (seats, beforeShow) -> RefundRule.refund(seats, 100, true);
    private static final RefundPolicySnapshot POLICY =
            new RefundPolicySnapshot(1, "Standard", RefundPolicyType.FULL, false, List.of());

    @Test
    void holdStartsHeldWithFrozenPrices() {
        Booking booking = hold();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(booking.getStatus().holdsSeats()).isTrue();
        assertThat(booking.getTotals()).isEqualTo(TOTALS);
        assertThat(booking.getSeats()).extracting(BookingSeat::seatLabel).containsExactly("F7", "F8");
    }

    @Test
    void seatAmountsMustAddUpToTheTotal() {
        PriceTotals wrong = new PriceTotals(40000, 0, 4000, 8000, 52000);

        assertThatThrownBy(() -> Booking.hold(UUID.randomUUID(), "BK000001", UUID.randomUUID(), 1,
                NOW.plusSeconds(86400), EXPIRES, SEATS, wrong, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceTotals(100, 0, 10, 10, 999)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseAndExpireCloseTheBooking() {
        Booking released = hold();
        released.release(NOW.plusSeconds(60));
        assertThat(released.getStatus()).isEqualTo(BookingStatus.RELEASED);
        assertThat(released.getClosedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(released.getStatus().holdsSeats()).isFalse();

        Booking expired = hold();
        expired.expire(EXPIRES);
        assertThat(expired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void closedBookingsCantChangeAgain() {
        Booking booking = hold();
        booking.release(NOW);

        assertThatThrownBy(() -> booking.expire(NOW))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessage("Booking cannot move from RELEASED to EXPIRED");
    }

    @Test
    void startingPaymentKeepsTheSeatsForThePaymentWindowButNeverShortensTheHold() {
        Booking early = hold();                                   // hold runs until NOW + 8 min
        early.startPayment(NOW, Duration.ofMinutes(5));
        assertThat(early.getStatus()).isEqualTo(BookingStatus.PAYMENT_PENDING);
        assertThat(early.getHoldExpiresAt()).isEqualTo(EXPIRES);

        Booking late = hold();
        late.startPayment(NOW.plus(Duration.ofMinutes(7)), Duration.ofMinutes(5));
        assertThat(late.getHoldExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(12)));
    }

    @Test
    void paymentEndsConfirmedOrFailed() {
        Booking paid = hold();
        paid.startPayment(NOW, Duration.ofMinutes(5));
        paid.confirm(POLICY, NOW.plusSeconds(30));
        assertThat(paid.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paid.getConfirmedAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(paid.getRefundPolicySnapshot()).isEqualTo(POLICY);

        Booking declined = hold();
        declined.startPayment(NOW, Duration.ofMinutes(5));
        declined.fail(NOW.plusSeconds(30));
        assertThat(declined.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(declined.getClosedAt()).isEqualTo(NOW.plusSeconds(30));

        assertThatThrownBy(() -> hold().confirm(POLICY, NOW))                     // not paid
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void holdExpiresExactlyAtItsExpiryTime() {
        Booking booking = hold();

        assertThat(booking.isHoldExpired(EXPIRES.minusSeconds(1))).isFalse();
        assertThat(booking.isHoldExpired(EXPIRES)).isTrue();
        booking.release(NOW);
        assertThat(booking.isHoldExpired(EXPIRES)).isFalse();     // only a live hold can expire
    }

    @Test
    void cancellingSomeSeatsKeepsTheBookingConfirmed() {
        Booking booking = confirmed();

        Cancellation first = booking.cancel(Set.of(12L), FULL_REFUND, CancellationReason.CUSTOMER, NOW);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(first.getSeatCount()).isEqualTo(1);
        assertThat(first.getRefundPaise()).isEqualTo(25960);
        assertThat(booking.getSeats()).extracting(BookingSeat::status)
                .containsExactly(BookingSeat.Status.ACTIVE, BookingSeat.Status.CANCELLED);   // F7, F8
        assertThat(booking.getSeats().get(1).cancellationId()).isEqualTo(first.getId());

        Cancellation rest = booking.cancel(Set.of(), FULL_REFUND, CancellationReason.CUSTOMER, NOW);
        assertThat(rest.getSeatCount()).isEqualTo(1);                             // only what was still active
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellations()).hasSize(2);
    }

    @Test
    void onlyActiveSeatsOfAConfirmedBookingCanBeCancelled() {
        Booking booking = confirmed();
        booking.cancel(Set.of(12L), FULL_REFUND, CancellationReason.CUSTOMER, NOW);

        assertThatThrownBy(() -> booking.cancel(Set.of(12L), FULL_REFUND, CancellationReason.CUSTOMER, NOW))
                .isInstanceOf(InvalidStateException.class).hasMessage("Seat F8 is already cancelled");
        assertThatThrownBy(() -> booking.refundQuote(Set.of(99L), FULL_REFUND, NOW))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> hold().refundQuote(Set.of(), FULL_REFUND, NOW))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void aReminderIsDueInsideTheLeadTimeAndOnlyOnce() {
        Booking booking = confirmed();                            // show at NOW + 24h
        Instant show = NOW.plusSeconds(86400);
        Duration twoHours = Duration.ofHours(2);

        assertThat(booking.isDueForReminder(show.minus(twoHours).minusSeconds(1), twoHours)).isFalse();
        assertThat(booking.isDueForReminder(show.minus(twoHours), twoHours)).isTrue();
        assertThat(booking.isDueForReminder(show, twoHours)).isFalse();             // already started
        assertThat(hold().isDueForReminder(show.minusSeconds(60), twoHours)).isFalse();

        booking.markReminded(show.minusSeconds(60));
        assertThat(booking.isDueForReminder(show.minusSeconds(30), twoHours)).isFalse();
        assertThatThrownBy(() -> booking.markReminded(show)).isInstanceOf(InvalidStateException.class);
    }

    private static Booking confirmed() {
        Booking booking = hold();
        booking.startPayment(NOW, Duration.ofMinutes(5));
        booking.confirm(POLICY, NOW);
        return booking;
    }

    private static Booking hold() {
        return Booking.hold(UUID.randomUUID(), "BK000001", UUID.randomUUID(), 1, NOW.plusSeconds(86400), EXPIRES,
                SEATS, TOTALS, null, NOW);
    }
}
