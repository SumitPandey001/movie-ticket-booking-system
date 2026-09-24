package com.sumit.movieticketbookingsystem.pricing.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/** Price in paise by category code, e.g. {@code {"prices": {"REGULAR": 30000, "PREMIUM": 36000}}}. */
record TheaterPricesRequest(@NotEmpty Map<@NotBlank String, @NotNull @Positive Long> prices) {
}
