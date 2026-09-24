package com.sumit.movieticketbookingsystem.booking;

import java.util.List;
import java.util.UUID;

/**
 * Some or all seats of a confirmed booking were cancelled. The refund, if any, follows as its own event.
 * seatLabels are the seats this cancellation covered; fullyCancelled means no active seats are left.
 */
public record BookingCancelled(UUID bookingId, String bookingRef, UUID userId, UUID cancellationId,
                               CancellationReason reason, String movieTitle, List<String> seatLabels,
                               long refundPaise, boolean fullyCancelled) {
}
