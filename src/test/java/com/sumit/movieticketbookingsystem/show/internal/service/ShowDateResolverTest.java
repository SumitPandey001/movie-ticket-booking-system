package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowDateResolverTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    private final ShowDateResolver resolver =
            new ShowDateResolver(new BookingProperties(Duration.ofMinutes(20), LocalTime.of(3, 0)));

    @Test
    void eveningShowIsListedOnItsOwnDate() {
        assertThat(resolver.showDate(ist("2026-10-03T21:30"), KOLKATA, null)).isEqualTo("2026-10-03");
    }

    @Test
    void lateNightShowIsListedUnderThePreviousDate() {
        assertThat(resolver.showDate(ist("2026-10-04T00:30"), KOLKATA, null)).isEqualTo("2026-10-03");
        assertThat(resolver.showDate(ist("2026-10-04T02:59"), KOLKATA, null)).isEqualTo("2026-10-03");
    }

    @Test
    void cutoffItselfStartsTheNewDay() {
        assertThat(resolver.showDate(ist("2026-10-04T03:00"), KOLKATA, null)).isEqualTo("2026-10-04");
    }

    @Test
    void usesTheCityTimezoneNotUtc() {
        // 22:00 UTC is 03:30 the next morning in Kolkata
        assertThat(resolver.showDate(Instant.parse("2026-10-03T22:00:00Z"), KOLKATA, null)).isEqualTo("2026-10-04");
    }

    @Test
    void adminMayPickTheCalendarDateOrTheDayBefore() {
        Instant halfPastMidnight = ist("2026-10-04T00:30");

        assertThat(resolver.showDate(halfPastMidnight, KOLKATA, LocalDate.parse("2026-10-04"))).isEqualTo("2026-10-04");
        assertThat(resolver.showDate(halfPastMidnight, KOLKATA, LocalDate.parse("2026-10-03"))).isEqualTo("2026-10-03");
        assertThatThrownBy(() -> resolver.showDate(halfPastMidnight, KOLKATA, LocalDate.parse("2026-10-05")))
                .isInstanceOf(ValidationException.class);
    }

    private static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(KOLKATA).toInstant();
    }
}
