package com.sumit.movieticketbookingsystem.shared;

import com.sumit.movieticketbookingsystem.shared.BookingProperties.Slot;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;

/**
 * The same values as application.yml, for unit tests that don't start Spring.
 */
public final class TestBookingProperties {

    private TestBookingProperties() {
    }

    public static BookingProperties defaults() {
        return new BookingProperties(Duration.ofMinutes(20), LocalTime.of(3, 0), Duration.ofMinutes(10), 7, 20,
                Map.of("MORNING", new Slot(LocalTime.of(3, 0), LocalTime.of(12, 0)),
                        "AFTERNOON", new Slot(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                        "EVENING", new Slot(LocalTime.of(16, 0), LocalTime.of(20, 0)),
                        "NIGHT", new Slot(LocalTime.of(20, 0), LocalTime.of(3, 0))));
    }
}
