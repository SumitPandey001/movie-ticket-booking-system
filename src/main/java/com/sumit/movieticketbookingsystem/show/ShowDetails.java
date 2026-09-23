package com.sumit.movieticketbookingsystem.show;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * @param open           whether the show is on sale (opened and not cancelled)
 * @param refundPolicyId null when the show uses the default refund policy
 */
public record ShowDetails(long showId, long movieId, long theaterId, long cityId, long layoutId, LocalDate showDate,
                          Instant startTime, boolean open, Long refundPolicyId) {

    /** Seats can be held until the booking cutoff before the start. */
    public boolean isBookable(Instant now, Duration bookingCutoff) {
        return open && now.isBefore(startTime.minus(bookingCutoff));
    }
}
