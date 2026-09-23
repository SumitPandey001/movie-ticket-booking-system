package com.sumit.movieticketbookingsystem.catalog.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code timezone} is optional and defaults to Asia/Kolkata.
 */
record CityRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 80) String state,
        @Size(max = 40) String timezone) {
}
