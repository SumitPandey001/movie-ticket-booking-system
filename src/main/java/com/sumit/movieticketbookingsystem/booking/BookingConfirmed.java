package com.sumit.movieticketbookingsystem.booking;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * A booking was paid for and its seats are the customer's. Carries everything a ticket email needs, including
 * the city's time zone so the show time reads the way the customer expects.
 */
public record BookingConfirmed(UUID bookingId, String bookingRef, UUID userId, String movieTitle,
                               String theaterName, Instant showStartTime, ZoneId zone, List<String> seatLabels,
                               long totalPaise) {
}
