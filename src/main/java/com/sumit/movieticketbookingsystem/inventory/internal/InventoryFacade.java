package com.sumit.movieticketbookingsystem.inventory.internal;

import com.sumit.movieticketbookingsystem.catalog.LayoutView;
import com.sumit.movieticketbookingsystem.inventory.InventoryApi;
import com.sumit.movieticketbookingsystem.inventory.SeatStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public Set<Long> block(long showId, Set<Long> seatIds) {
        return changeStatus(showId, seatIds, "AVAILABLE", "BLOCKED");
    }

    @Override
    public Set<Long> unblock(long showId, Set<Long> seatIds) {
        return changeStatus(showId, seatIds, "BLOCKED", "AVAILABLE");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, SeatStatus> seatStatuses(long showId) {
        return seats.statuses(showId);
    }

    /** @return the requested seats that didn't change */
    private Set<Long> changeStatus(long showId, Set<Long> seatIds, String from, String to) {
        List<Long> changed = seats.changeStatus(showId, seatIds, from, to);
        if (!changed.isEmpty()) {
            events.publishEvent(new SeatAvailabilityChanged(showId));
        }
        Set<Long> unchanged = new HashSet<>(seatIds);
        changed.forEach(unchanged::remove);
        return unchanged;
    }
}
