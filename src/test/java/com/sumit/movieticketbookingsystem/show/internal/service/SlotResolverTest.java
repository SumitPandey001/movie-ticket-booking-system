package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.TestBookingProperties;
import com.sumit.movieticketbookingsystem.shared.TimeWindow;
import com.sumit.movieticketbookingsystem.show.internal.domain.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotResolverTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final LocalDate OCT_3 = LocalDate.parse("2026-10-03");

    private final SlotResolver resolver = new SlotResolver(TestBookingProperties.defaults());

    @Test
    void daySlotsStayWithinTheListingDate() {
        TimeWindow evening = resolver.window(TimeSlot.EVENING, OCT_3, KOLKATA);

        assertThat(evening.from()).isEqualTo(ist("2026-10-03T16:00"));
        assertThat(evening.to()).isEqualTo(ist("2026-10-03T20:00"));
        assertThat(evening.contains(ist("2026-10-03T19:59"))).isTrue();
        assertThat(evening.contains(ist("2026-10-03T20:00"))).isFalse();
    }

    @Test
    void nightRunsPastMidnightAndCoversLateNightShows() {
        TimeWindow night = resolver.window(TimeSlot.NIGHT, OCT_3, KOLKATA);

        assertThat(night.to()).isEqualTo(ist("2026-10-04T03:00"));
        // a 00:30 show on Oct 4 is listed under Oct 3, and it's a NIGHT show there
        assertThat(night.contains(ist("2026-10-04T00:30"))).isTrue();
        assertThat(resolver.window(TimeSlot.MORNING, OCT_3.plusDays(1), KOLKATA)
                .contains(ist("2026-10-04T00:30"))).isFalse();
    }

    @Test
    void everySlotMustBeConfigured() {
        BookingProperties defaults = TestBookingProperties.defaults();
        Map<String, BookingProperties.Slot> withoutNight = new HashMap<>(defaults.slots());
        withoutNight.remove("NIGHT");
        BookingProperties incomplete = new BookingProperties(defaults.cleaningBuffer(), defaults.lateNightCutoff(),
                defaults.bookingCutoff(), defaults.dateStripDays(), defaults.fillingFastPercent(), withoutNight);

        assertThatThrownBy(() -> new SlotResolver(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("booking.slots.NIGHT is not configured");
    }

    private static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(KOLKATA).toInstant();
    }
}
