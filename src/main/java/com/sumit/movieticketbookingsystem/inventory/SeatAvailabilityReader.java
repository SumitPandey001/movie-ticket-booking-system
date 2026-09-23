package com.sumit.movieticketbookingsystem.inventory;

import java.util.Collection;
import java.util.Map;

/**
 * Seats-left numbers for listing pages. For display only: booking decisions always go through {@link InventoryApi}.
 */
public interface SeatAvailabilityReader {

    /** Available seats by show id; shows without seat rows are left out. */
    Map<Long, Integer> seatsLeft(Collection<Long> showIds);
}
