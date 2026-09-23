package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Picks the date a show is listed under. A 00:30 show belongs to the previous evening from the audience's
 * point of view, so anything starting before the late-night cutoff goes under the day before.
 */
@Component
class ShowDateResolver {

    private final LocalTime lateNightCutoff;

    ShowDateResolver(BookingProperties properties) {
        this.lateNightCutoff = properties.lateNightCutoff();
    }

    /**
     * @param requested the admin's choice, if any; only the calendar date or the day before make sense
     */
    LocalDate showDate(Instant start, ZoneId zone, LocalDate requested) {
        if (requested == null) {
            return listingDate(start, zone);
        }
        LocalDate calendarDate = start.atZone(zone).toLocalDate();
        if (!requested.equals(calendarDate) && !requested.equals(calendarDate.minusDays(1))) {
            throw new ValidationException("Show date " + requested + " must be " + calendarDate + " or the day before");
        }
        return requested;
    }

    /** The listing date a moment belongs to; at 00:30 it's still "yesterday" for the browse page. */
    LocalDate listingDate(Instant at, ZoneId zone) {
        ZonedDateTime local = at.atZone(zone);
        return local.toLocalTime().isBefore(lateNightCutoff) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }
}
