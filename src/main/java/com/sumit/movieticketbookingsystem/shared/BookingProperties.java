package com.sumit.movieticketbookingsystem.shared;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Business rules from the {@code booking.*} block in application.yml, checked at startup.
 *
 * @param cleaningBuffer  gap kept free on a screen after each show ends
 * @param lateNightCutoff shows starting before this local time are listed under the previous date
 */
@Validated
@ConfigurationProperties("booking")
public record BookingProperties(
        @NotNull Duration cleaningBuffer,
        @NotNull LocalTime lateNightCutoff) {
}
