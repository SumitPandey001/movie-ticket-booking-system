package com.sumit.movieticketbookingsystem.show.internal.web;

import java.util.Set;

/** Seats left as they were, e.g. blocking a seat that's already blocked. Empty means every seat changed. */
record SeatChangeResponse(Set<Long> unchangedSeatIds) {
}
