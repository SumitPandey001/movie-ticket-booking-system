package com.sumit.movieticketbookingsystem.booking.internal.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

record HoldRequest(@NotNull Long showId, @NotEmpty Set<@NotNull Long> seatIds) {
}
