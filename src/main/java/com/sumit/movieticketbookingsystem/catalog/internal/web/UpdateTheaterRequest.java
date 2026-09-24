package com.sumit.movieticketbookingsystem.catalog.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record UpdateTheaterRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String area,
        @Size(max = 300) String address) {
}
