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
import java.util.List;
import java.util.Map;

/**
 * Business rules from the booking block in application.yml, checked at startup.
 */
@Validated
@ConfigurationProperties("booking")
public record BookingProperties(
        @NotNull Duration cleaningBuffer,             // screen kept free after each show
        @NotNull LocalTime lateNightCutoff,           // shows starting before this are listed under the previous day
        @NotNull Duration bookingCutoff,              // sales stop this long before the start
        @Min(1) @Max(31) int dateStripDays,           // days customers can browse ahead, today included
        @Min(1) @Max(100) int fillingFastPercent,     // "filling fast" below this share of seats left
        @NotEmpty Map<String, @Valid Slot> slots,     // a slot whose to is before its from ends the next day
        @NotNull @Valid Cache cache,
        @NotNull Duration holdDuration,
        @NotNull Duration paymentWindow,              // starting to pay keeps the seats at least this long
        @NotNull Duration paymentGrace,               // extra time an unanswered payment gets before the sweeper
        @NotNull Duration cancellationCutoff,         // no customer cancellations this close to the show
        @NotNull Duration reminderLeadTime,
        @Min(1) @Max(50) int maxSeatsPerBooking,
        @Min(0) long convenienceFeePaise,
        @Min(0) @Max(100) int gstPercent,             // on the ticket after discount, and on the fee
        @NotNull Duration idempotencyRetention,
        @NotNull @Valid Payment payment) {

    public record Slot(@NotNull LocalTime from, @NotNull LocalTime to) {
    }

    public record Cache(@NotNull Duration showDayTtl, @NotNull Duration seatCounterTtl) {
    }

    // simulatedDelay is how long a DELAYED simulated payment takes before it succeeds
    public record Payment(@NotNull Duration simulatedDelay, @NotEmpty List<String> netBankingBanks,
                          @NotEmpty List<String> wallets) {
    }
}
