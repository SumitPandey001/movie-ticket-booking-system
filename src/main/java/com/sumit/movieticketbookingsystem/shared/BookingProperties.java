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
 * Business rules from the {@code booking.*} block in application.yml, checked at startup.
 *
 * @param cleaningBuffer     gap kept free on a screen after each show ends
 * @param lateNightCutoff    shows starting before this local time are listed under the previous date
 * @param bookingCutoff      how long before the start a show stops being sold (and drops off the browse page)
 * @param dateStripDays      how many days ahead customers can browse, today included
 * @param fillingFastPercent a show is "filling fast" below this share of seats left
 * @param slots              time-of-day filters by name; a {@code to} earlier than {@code from} ends the next day
 * @param cache              how long Redis keeps the browse cache and the seats-left counters
 * @param holdDuration       how long held seats stay reserved while the customer pays
 * @param paymentWindow      starting a payment keeps the seats at least this long, so a slow gateway doesn't lose them
 * @param paymentGrace       extra time a pending payment gets past its window before the sweeper expires it
 * @param cancellationCutoff customers can't cancel once the show is closer than this
 * @param maxSeatsPerBooking most seats one booking may hold
 * @param convenienceFeePaise flat fee per seat
 * @param gstPercent         GST on tickets (after discount) and on the convenience fee
 * @param idempotencyRetention how long a request's Idempotency-Key and saved answer are kept
 * @param payment            what the simulated payment methods accept
 */
@Validated
@ConfigurationProperties("booking")
public record BookingProperties(
        @NotNull Duration cleaningBuffer,
        @NotNull LocalTime lateNightCutoff,
        @NotNull Duration bookingCutoff,
        @Min(1) @Max(31) int dateStripDays,
        @Min(1) @Max(100) int fillingFastPercent,
        @NotEmpty Map<String, @Valid Slot> slots,
        @NotNull @Valid Cache cache,
        @NotNull Duration holdDuration,
        @NotNull Duration paymentWindow,
        @NotNull Duration paymentGrace,
        @NotNull Duration cancellationCutoff,
        @Min(1) @Max(50) int maxSeatsPerBooking,
        @Min(0) long convenienceFeePaise,
        @Min(0) @Max(100) int gstPercent,
        @NotNull Duration idempotencyRetention,
        @NotNull @Valid Payment payment) {

    public record Slot(@NotNull LocalTime from, @NotNull LocalTime to) {
    }

    public record Cache(@NotNull Duration showDayTtl, @NotNull Duration seatCounterTtl) {
    }

    /**
     * @param simulatedDelay how long a DELAYED simulated payment takes before it succeeds
     */
    public record Payment(@NotNull Duration simulatedDelay, @NotEmpty List<String> netBankingBanks,
                          @NotEmpty List<String> wallets) {
    }
}
