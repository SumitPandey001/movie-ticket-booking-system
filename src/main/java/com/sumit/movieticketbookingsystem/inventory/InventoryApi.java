package com.sumit.movieticketbookingsystem.inventory;

import com.sumit.movieticketbookingsystem.catalog.LayoutView;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    /**
     * Claims every requested seat for the booking, or none of them. Seats are free when they're available or
     * their hold ran out before {@code now}. Rows are locked in seat order and never waited on, so two holds
     * can't deadlock and a customer racing for the same seat gets an answer straight away.
     *
     * @throws SeatsUnavailableException if any seat is taken, locked by another hold, or not part of the show
     */
    List<HeldSeat> hold(long showId, Set<Long> seatIds, UUID bookingId, Instant expiresAt, Instant now);

    /** Frees the seats the booking still holds (release, expiry, failed payment); returns how many. */
    int releaseHeld(long showId, UUID bookingId);

    /**
     * Current status of every seat of the show, by layout seat id. Always read from the database;
     * a hold that ran out before {@code now} reads as AVAILABLE.
     */
    Map<Long, SeatStatus> seatStatuses(long showId, Instant now);
}
