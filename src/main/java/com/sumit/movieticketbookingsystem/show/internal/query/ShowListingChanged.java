package com.sumit.movieticketbookingsystem.show.internal.query;

import java.time.LocalDate;

/**
 * A show changed in a way the browse page can see (opened, cancelled, re-priced).
 */
public record ShowListingChanged(long cityId, long movieId, LocalDate showDate) {
}
