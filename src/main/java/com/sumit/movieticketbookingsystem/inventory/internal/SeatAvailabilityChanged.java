package com.sumit.movieticketbookingsystem.inventory.internal;

/**
 * The number of available seats of a show went up or down by {@code delta}.
 */
record SeatAvailabilityChanged(long showId, int delta) {
}
