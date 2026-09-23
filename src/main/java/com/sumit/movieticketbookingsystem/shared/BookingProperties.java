package com.sumit.movieticketbookingsystem.shared;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;

/**
 * Business rules from the {@code booking.*} block in application.yml, checked at startup.
 *
 * @param cleaningBuffer     gap kept free on a screen after each show ends
 * @param lateNightCutoff    shows starting before this local time are listed under the previous date
 * @param bookingCutoff      how long before the start a show stops being sold (and drops off the browse page)
 * @param dateStripDays      how many days ahead customers can browse, today included
 * @param fillingFastPercent a show is "filling fast" below this share of seats left
 * @param slots              time-of-day filters by name; a {@code to} earlier than {@code from} ends the next day
 */
@Validated
@ConfigurationProperties("booking")
public record BookingProperties(
        @NotNull Duration cleaningBuffer,
        @NotNull LocalTime lateNightCutoff,
        @NotNull Duration bookingCutoff,
        @Min(1) @Max(31) int dateStripDays,
        @Min(1) @Max(100) int fillingFastPercent,
        @NotEmpty Map<String, @Valid Slot> slots) {

    public record Slot(@NotNull LocalTime from, @NotNull LocalTime to) {
    }
}
