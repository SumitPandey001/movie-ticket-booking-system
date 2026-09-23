package com.sumit.movieticketbookingsystem.shared;

import com.sumit.movieticketbookingsystem.shared.BookingProperties.Slot;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * The same values as application.yml, for unit tests that don't start Spring.
 */
public final class TestBookingProperties {

    private TestBookingProperties() {
    }

    public static BookingProperties defaults() {
        return withSlots(Map.of("MORNING", new Slot(LocalTime.of(3, 0), LocalTime.of(12, 0)),
                "AFTERNOON", new Slot(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                "EVENING", new Slot(LocalTime.of(16, 0), LocalTime.of(20, 0)),
                "NIGHT", new Slot(LocalTime.of(20, 0), LocalTime.of(3, 0))));
    }

    public static BookingProperties withSlots(Map<String, Slot> slots) {
        return new BookingProperties(Duration.ofMinutes(20), LocalTime.of(3, 0), Duration.ofMinutes(10), 7, 20, slots,
                new BookingProperties.Cache(Duration.ofSeconds(60), Duration.ofMinutes(10)),
                Duration.ofMinutes(8), Duration.ofMinutes(5), 10, 2000, 18, Duration.ofHours(24),
                new BookingProperties.Payment(List.of("HDFC", "ICICI", "SBI"), List.of("PAYTM", "PHONEPE")));
    }
}
