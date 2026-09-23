package com.sumit.movieticketbookingsystem.show.internal.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

record SeatIdsRequest(@NotEmpty @Size(max = 500) Set<Long> seatIds) {
}
