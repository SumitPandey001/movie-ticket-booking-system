package com.sumit.movieticketbookingsystem.booking;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * A confirmed booking's show starts soon. Published once per booking.
 *
 * @param seatLabels the seats still booked; cancelled ones are left out
 */
public record ReminderDue(UUID bookingId, String bookingRef, UUID userId, String movieTitle, String theaterName,
                          Instant showStartTime, ZoneId zone, List<String> seatLabels) {
}
