package com.sumit.movieticketbookingsystem.show.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * {@code startTime} carries its offset, e.g. {@code 2026-10-03T18:15:00+05:30}. {@code showDate} is optional.
 * {@code priceOverrides} is optional too: price in paise by category code, replacing the theater's default.
 * {@code refundPolicyId} is optional; without it the default refund policy applies.
 */
record CreateShowRequest(
        @NotNull Long movieId,
        @NotNull Long screenId,
        @NotNull OffsetDateTime startTime,
        @NotBlank @Size(max = 10) String language,
        @NotBlank @Size(max = 10) String format,
        LocalDate showDate,
        Map<@NotBlank String, @NotNull @Positive Long> priceOverrides,
        Long refundPolicyId) {

    Map<String, Long> priceOverridesOrEmpty() {
        return priceOverrides == null ? Map.of() : priceOverrides;
    }
}
