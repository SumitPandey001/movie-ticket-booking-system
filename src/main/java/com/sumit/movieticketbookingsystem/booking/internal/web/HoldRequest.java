package com.sumit.movieticketbookingsystem.booking.internal.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * {@code couponCode} is optional.
 */
record HoldRequest(@NotNull Long showId, @NotEmpty Set<@NotNull Long> seatIds, @Size(max = 30) String couponCode) {
}
