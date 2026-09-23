package com.sumit.movieticketbookingsystem.booking;

import java.util.List;
import java.util.UUID;

/**
 * Some or all seats of a confirmed booking were cancelled. The refund, if any, follows as its own event.
 *
 * @param seatLabels     the seats this cancellation covered
 * @param fullyCancelled no active seats are left on the booking
 */
public record BookingCancelled(UUID bookingId, String bookingRef, UUID userId, UUID cancellationId,
                               CancellationReason reason, String movieTitle, List<String> seatLabels,
                               long refundPaise, boolean fullyCancelled) {
}
