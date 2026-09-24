package com.sumit.movieticketbookingsystem.catalog.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ScreenNameRequest(@NotBlank @Size(max = 40) String name) {
}
