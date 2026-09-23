package com.sumit.movieticketbookingsystem.show;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * @param open whether the show is on sale (opened and not cancelled)
 */
public record ShowDetails(long showId, long movieId, long theaterId, long cityId, long layoutId, LocalDate showDate,
                          Instant startTime, boolean open) {

    /** Seats can be held until the booking cutoff before the start. */
    public boolean isBookable(Instant now, Duration bookingCutoff) {
        return open && now.isBefore(startTime.minus(bookingCutoff));
    }
}
