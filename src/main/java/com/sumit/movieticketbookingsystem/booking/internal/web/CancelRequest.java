package com.sumit.movieticketbookingsystem.booking.internal.web;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** seatIds optional; leave it out to cancel every seat that's still active. */
record CancelRequest(Set<@NotNull Long> seatIds) {
}
