package com.sumit.movieticketbookingsystem.show.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * {@code startTime} carries its offset, e.g. {@code 2026-10-03T18:15:00+05:30}. {@code showDate} is optional.
 */
record CreateShowRequest(
        @NotNull Long movieId,
        @NotNull Long screenId,
        @NotNull OffsetDateTime startTime,
        @NotBlank @Size(max = 10) String language,
        @NotBlank @Size(max = 10) String format,
        LocalDate showDate) {
}
