package com.sumit.movieticketbookingsystem;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Show dates for tests that check prices, since the seeded "Weekend +20%" rule changes prices on Saturdays and
 * Sundays. Dates are in IST, the fixtures' city timezone.
 */
public final class TestDates {

    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private TestDates() {
    }

    /** The first Monday-to-Friday date at least daysAhead days from today. */
    public static LocalDate weekday(int daysAhead) {
        LocalDate date = LocalDate.now(IST).plusDays(daysAhead);
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    /** The first Saturday at least daysAhead days from today. */
    public static LocalDate saturday(int daysAhead) {
        LocalDate date = LocalDate.now(IST).plusDays(daysAhead);
        while (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            date = date.plusDays(1);
        }
        return date;
    }
}
