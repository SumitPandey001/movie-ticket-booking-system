package com.sumit.movieticketbookingsystem.inventory.internal;

import com.sumit.movieticketbookingsystem.catalog.LayoutView;
import com.sumit.movieticketbookingsystem.inventory.HeldSeat;
import com.sumit.movieticketbookingsystem.inventory.InventoryApi;
import com.sumit.movieticketbookingsystem.inventory.SeatStatus;
import com.sumit.movieticketbookingsystem.inventory.SeatsUnavailableException;
import com.sumit.movieticketbookingsystem.inventory.internal.SeatInventoryRepository.ClaimedSeat;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
class InventoryFacade implements InventoryApi {

    private final SeatInventoryRepository seats;
    private final ApplicationEventPublisher events;

    InventoryFacade(SeatInventoryRepository seats, ApplicationEventPublisher events) {
        this.seats = seats;
        this.events = events;
    }

    @Override
    public void initializeSeats(long showId, LayoutView layout) {
        seats.insertSeats(showId, layout.seats());
    }

    @Override
    public List<HeldSeat> hold(long showId, Set<Long> seatIds, UUID bookingId, Instant expiresAt, Instant now) {
        if (seatIds.isEmpty()) {
            throw new IllegalArgumentException("Nothing to hold");
        }
        List<ClaimedSeat> claimed;
        try {
            claimed = seats.hold(showId, seatIds, bookingId, expiresAt, now);
        } catch (DataAccessException e) {
            if (!isLockNotAvailable(e)) {
                throw e;
            }
            // another hold has some of these rows locked right now; to the customer that's "taken"
            throw new SeatsUnavailableException(seatIds);
        }
        if (claimed.size() < seatIds.size()) {
            Set<Long> taken = new HashSet<>(seatIds);
            claimed.forEach(seat -> taken.remove(seat.layoutSeatId()));
            throw new SeatsUnavailableException(taken);   // rolls back the seats that were claimed
        }

        // a seat taken over from an expired hold was already counted as gone
        long wereAvailable = claimed.stream().filter(seat -> seat.previousStatus() == SeatStatus.AVAILABLE).count();
        availabilityChanged(showId, -Math.toIntExact(wereAvailable));
        return claimed.stream()
                .map(seat -> new HeldSeat(seat.layoutSeatId(), seat.label(), seat.categoryId()))
                .toList();
    }

    @Override
    public void confirm(long showId, Set<Long> seatIds, UUID bookingId, Instant now) {
        List<ClaimedSeat> booked = seats.confirm(showId, seatIds, bookingId, now);
        if (booked.size() < seatIds.size()) {
            Set<Long> lost = new HashSet<>(seatIds);
            booked.forEach(seat -> lost.remove(seat.layoutSeatId()));
            throw new SeatsUnavailableException(lost);   // rolls back the seats that were booked
        }
        // a seat that had become free again comes off the counter now; our own held seats already had
        long wereAvailable = booked.stream().filter(seat -> seat.previousStatus() == SeatStatus.AVAILABLE).count();
        availabilityChanged(showId, -Math.toIntExact(wereAvailable));
    }

    @Override
    public void releaseBooked(long showId, Set<Long> seatIds, UUID bookingId) {
        int released = seats.releaseBooked(showId, seatIds, bookingId);
        if (released != seatIds.size()) {
            throw new IllegalStateException(
                    "Only " + released + " of " + seatIds.size() + " seats are booked by " + bookingId);
        }
        availabilityChanged(showId, released);
    }

    @Override
    public int releaseHeld(long showId, UUID bookingId) {
        int released = seats.releaseHeld(showId, bookingId);
        availabilityChanged(showId, released);
        return released;
    }

    @Override
    public Set<Long> block(long showId, Set<Long> seatIds) {
        List<Long> changed = seats.changeStatus(showId, seatIds, "AVAILABLE", "BLOCKED");
        availabilityChanged(showId, -changed.size());
        return unchanged(seatIds, changed);
    }

    @Override
    public Set<Long> unblock(long showId, Set<Long> seatIds) {
        List<Long> changed = seats.changeStatus(showId, seatIds, "BLOCKED", "AVAILABLE");
        availabilityChanged(showId, changed.size());
        return unchanged(seatIds, changed);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, SeatStatus> seatStatuses(long showId, Instant now) {
        return seats.statuses(showId, now);
    }

    private void availabilityChanged(long showId, int delta) {
        if (delta != 0) {
            events.publishEvent(new SeatAvailabilityChanged(showId, delta));
        }
    }

    // 55P03, raised by FOR UPDATE NOWAIT. Checked by SQL state because Spring doesn't translate it to a
    // locking exception for Postgres; it arrives as an UncategorizedSQLException.
    private static boolean isLockNotAvailable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static Set<Long> unchanged(Set<Long> requested, List<Long> changed) {
        Set<Long> unchanged = new HashSet<>(requested);
        changed.forEach(unchanged::remove);
        return unchanged;
    }
}
