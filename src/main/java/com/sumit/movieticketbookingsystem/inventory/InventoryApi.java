package com.sumit.movieticketbookingsystem.inventory;

import com.sumit.movieticketbookingsystem.catalog.LayoutView;

import java.util.Map;
import java.util.Set;

/**
 * The seats of each show. Every change is a conditional update on the seat rows, so it's safe under concurrency.
 */
public interface InventoryApi {

    /** Creates the show's seat rows from its layout; seats blocked in the layout start out blocked. */
    void initializeSeats(long showId, LayoutView layout);

    /** Blocks seats that are available and returns the ids that weren't, e.g. already blocked or unknown. */
    Set<Long> block(long showId, Set<Long> seatIds);

    /** Unblocks seats that are blocked and returns the ids that weren't. */
    Set<Long> unblock(long showId, Set<Long> seatIds);

    /** Current status of every seat of the show, by layout seat id. Always read from the database. */
    Map<Long, SeatStatus> seatStatuses(long showId);
}
