package com.sumit.movieticketbookingsystem.inventory.internal;

/**
 * Some seats of the show changed between available and not; its seats-left counter is out of date.
 */
record SeatAvailabilityChanged(long showId) {
}
