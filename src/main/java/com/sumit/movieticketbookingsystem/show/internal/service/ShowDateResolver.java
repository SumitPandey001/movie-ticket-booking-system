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
        ZonedDateTime local = start.atZone(zone);
        LocalDate calendarDate = local.toLocalDate();
        if (requested != null) {
            if (!requested.equals(calendarDate) && !requested.equals(calendarDate.minusDays(1))) {
                throw new ValidationException("Show date " + requested + " must be " + calendarDate
                        + " or the day before");
            }
            return requested;
        }
        return local.toLocalTime().isBefore(lateNightCutoff) ? calendarDate.minusDays(1) : calendarDate;
    }
}
