package com.sumit.movieticketbookingsystem.booking.internal.service;

import com.sumit.movieticketbookingsystem.booking.internal.domain.Booking;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingRefGenerator;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingSeat;
import com.sumit.movieticketbookingsystem.booking.internal.domain.BookingStatus;
import com.sumit.movieticketbookingsystem.booking.internal.domain.PriceTotals;
import com.sumit.movieticketbookingsystem.booking.internal.persistence.BookingRepository;
import com.sumit.movieticketbookingsystem.inventory.HeldSeat;
import com.sumit.movieticketbookingsystem.inventory.InventoryApi;
import com.sumit.movieticketbookingsystem.pricing.PriceQuote;
import com.sumit.movieticketbookingsystem.pricing.PricingApi;
import com.sumit.movieticketbookingsystem.pricing.SeatPriceLine;
import com.sumit.movieticketbookingsystem.pricing.SeatToPrice;
import com.sumit.movieticketbookingsystem.pricing.ShowPricing;
import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import com.sumit.movieticketbookingsystem.shared.persistence.ConstraintViolations;
import com.sumit.movieticketbookingsystem.show.ShowApi;
import com.sumit.movieticketbookingsystem.show.ShowDetails;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holding seats, letting them go, and looking up a customer's booking. A hold is one transaction across
 * inventory, pricing and booking, so it either happens completely or not at all.
 */
@Service
public class HoldService {

    private static final Set<BookingStatus> LIVE = EnumSet.of(BookingStatus.HELD, BookingStatus.PAYMENT_PENDING);

    private final BookingRepository bookings;
    private final ShowApi shows;
    private final InventoryApi inventory;
    private final PricingApi pricing;
    private final BookingRefGenerator refs;
    private final BookingProperties properties;
    private final Clock clock;

    HoldService(BookingRepository bookings, ShowApi shows, InventoryApi inventory, PricingApi pricing,
            BookingRefGenerator refs, BookingProperties properties, Clock clock) {
        this.bookings = bookings;
        this.shows = shows;
        this.inventory = inventory;
        this.pricing = pricing;
        this.refs = refs;
        this.properties = properties;
        this.clock = clock;
    }

    public record CreateHold(UUID userId, long showId, Set<Long> seatIds) {
    }

    @Transactional
    public Booking createHold(CreateHold command) {
        Instant now = Instant.now(clock);
        if (command.seatIds().isEmpty() || command.seatIds().size() > properties.maxSeatsPerBooking()) {
            throw new ValidationException("Pick between 1 and " + properties.maxSeatsPerBooking() + " seats");
        }
        ShowDetails show = shows.show(command.showId());
        if (!show.isBookable(now, properties.bookingCutoff())) {
            throw new ShowNotBookableException(show.showId());
        }
        replaceLapsedHold(command.userId(), show.showId(), now);

        UUID bookingId = UUID.randomUUID();
        List<HeldSeat> held = inventory.hold(show.showId(), command.seatIds(), bookingId,
                now.plus(properties.holdDuration()), now);
        PriceQuote quote = pricing.quote(
                new ShowPricing(show.showId(), show.cityId(), show.theaterId(), show.showDate()),
                held.stream().map(seat -> new SeatToPrice(seat.layoutSeatId(), seat.categoryId())).toList());

        // ponytail: a booking_ref clash (1 in ~10^9 per hold) fails this hold with a 500; retry with a fresh
        // ref here if that ever shows up in the logs
        Booking booking = Booking.hold(bookingId, refs.next(), command.userId(), show.showId(), show.startTime(),
                now.plus(properties.holdDuration()), seats(held, quote), totals(quote), now);
        try {
            return bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, "booking_one_active_hold")) {
                throw new ActiveHoldExistsException(null);   // a parallel request from the same customer won
            }
            throw e;
        }
    }

    @Transactional
    public Booking release(UUID bookingId, UUID userId) {
        Booking booking = ownBooking(bookingId, userId);
        booking.release(Instant.now(clock));
        inventory.releaseHeld(booking.getShowId(), booking.getId());
        return booking;
    }

    @Transactional(readOnly = true)
    public Booking booking(UUID bookingId, UUID userId) {
        return ownBooking(bookingId, userId);
    }

    /**
     * A customer's old hold on the same show that has already run out is closed so they can hold again.
     * The flush matters: Hibernate runs inserts before updates, so without it the new booking would hit
     * booking_one_active_hold while the old one still looks live.
     */
    private void replaceLapsedHold(UUID userId, long showId, Instant now) {
        Optional<Booking> live = bookings.findByUserIdAndShowIdAndStatusIn(userId, showId, LIVE);
        if (live.isEmpty()) {
            return;
        }
        Booking existing = live.get();
        if (!existing.isHoldExpired(now)) {
            throw new ActiveHoldExistsException(existing.getId());
        }
        existing.expire(now);
        inventory.releaseHeld(showId, existing.getId());
        bookings.saveAndFlush(existing);
    }

    // Someone else's booking is reported as not found, so the API never confirms that it exists.
    private Booking ownBooking(UUID bookingId, UUID userId) {
        return bookings.findById(bookingId)
                .filter(booking -> booking.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Booking", bookingId));
    }

    private static List<BookingSeat> seats(List<HeldSeat> held, PriceQuote quote) {
        Map<Long, SeatPriceLine> lines = quote.lines().stream()
                .collect(Collectors.toMap(SeatPriceLine::layoutSeatId, Function.identity()));
        return held.stream()
                .map(seat -> {
                    SeatPriceLine line = lines.get(seat.layoutSeatId());
                    return new BookingSeat(seat.layoutSeatId(), seat.label(), seat.categoryId(), line.basePaise(),
                            line.discountPaise(), line.feePaise() + line.feeTaxPaise(), line.amountPaise());
                })
                .toList();
    }

    private static PriceTotals totals(PriceQuote quote) {
        return new PriceTotals(quote.subtotalPaise(), quote.discountPaise(), quote.feePaise(), quote.taxPaise(),
                quote.totalPaise());
    }
}
