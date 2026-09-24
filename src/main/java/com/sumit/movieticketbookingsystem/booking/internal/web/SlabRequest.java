package com.sumit.movieticketbookingsystem.booking.internal.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record SlabRequest(@NotNull @Min(0) Integer minHoursBefore, @NotNull @Min(0) @Max(100) Integer percent) {
}
