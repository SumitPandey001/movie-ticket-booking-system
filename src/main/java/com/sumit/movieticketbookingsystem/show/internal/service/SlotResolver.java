package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.TimeWindow;
import com.sumit.movieticketbookingsystem.show.internal.domain.TimeSlot;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

@Component
class SlotResolver {

    private final Map<TimeSlot, BookingProperties.Slot> slots = new EnumMap<>(TimeSlot.class);

    SlotResolver(BookingProperties properties) {
        for (TimeSlot slot : TimeSlot.values()) {
            BookingProperties.Slot hours = properties.slots().get(slot.name());
            if (hours == null) {
                throw new IllegalStateException("booking.slots." + slot + " is not configured");
            }
            slots.put(slot, hours);
        }
    }

    /**
     * The slot's hours on a listing date, in the city's timezone. A slot whose end is before its start
     * (NIGHT, 20:00-03:00) runs into the next calendar day, which is where late-night shows fall.
     */
    TimeWindow window(TimeSlot slot, LocalDate listingDate, ZoneId zone) {
        BookingProperties.Slot hours = slots.get(slot);
        LocalDate endDate = hours.to().isAfter(hours.from()) ? listingDate : listingDate.plusDays(1);
        return new TimeWindow(
                listingDate.atTime(hours.from()).atZone(zone).toInstant(),
                endDate.atTime(hours.to()).atZone(zone).toInstant());
    }
}
