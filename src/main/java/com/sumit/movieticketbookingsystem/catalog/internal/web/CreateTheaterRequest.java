package com.sumit.movieticketbookingsystem.catalog.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateTheaterRequest(
        @NotNull Long cityId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String area,
        @Size(max = 300) String address) {
}
