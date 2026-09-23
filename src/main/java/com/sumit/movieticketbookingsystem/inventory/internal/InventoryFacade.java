package com.sumit.movieticketbookingsystem.inventory.internal;

import com.sumit.movieticketbookingsystem.catalog.LayoutView;
import com.sumit.movieticketbookingsystem.inventory.InventoryApi;
import com.sumit.movieticketbookingsystem.inventory.SeatAvailabilityReader;
import com.sumit.movieticketbookingsystem.inventory.SeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
class InventoryFacade implements InventoryApi, SeatAvailabilityReader {

    private final SeatInventoryRepository seats;

    InventoryFacade(SeatInventoryRepository seats) {
        this.seats = seats;
    }

    @Override
    public void initializeSeats(long showId, LayoutView layout) {
        seats.insertSeats(showId, layout.seats());
    }

    @Override
    public Set<Long> block(long showId, Set<Long> seatIds) {
        return unchanged(seatIds, seats.changeStatus(showId, seatIds, "AVAILABLE", "BLOCKED"));
    }

    @Override
    public Set<Long> unblock(long showId, Set<Long> seatIds) {
        return unchanged(seatIds, seats.changeStatus(showId, seatIds, "BLOCKED", "AVAILABLE"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, SeatStatus> seatStatuses(long showId) {
        return seats.statuses(showId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> seatsLeft(Collection<Long> showIds) {
        return seats.availableCounts(showIds);
    }

    private static Set<Long> unchanged(Set<Long> requested, Iterable<Long> changed) {
        Set<Long> unchanged = new HashSet<>(requested);
        changed.forEach(unchanged::remove);
        return unchanged;
    }
}
