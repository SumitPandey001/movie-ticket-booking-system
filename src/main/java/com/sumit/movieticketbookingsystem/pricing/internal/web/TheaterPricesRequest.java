package com.sumit.movieticketbookingsystem.pricing.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

record TheaterPricesRequest(@NotEmpty Map<@NotBlank String, @NotNull @Positive Long> prices) {
}
