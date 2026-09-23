package com.sumit.movieticketbookingsystem.booking.internal.domain;

import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicySnapshot;
import com.sumit.movieticketbookingsystem.booking.internal.refund.RefundPolicyType;
import com.sumit.movieticketbookingsystem.shared.error.IllegalTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    private static Booking hold() {
        return Booking.hold(UUID.randomUUID(), "BK000001", UUID.randomUUID(), 1, NOW.plusSeconds(86400), EXPIRES,
                SEATS, TOTALS, null, NOW);
    }
}
