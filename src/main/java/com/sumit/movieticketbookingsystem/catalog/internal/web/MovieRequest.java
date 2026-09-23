package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.Certification;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

record MovieRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Min(1) @Max(600) Integer durationMin,
        @NotNull Certification certification,
        LocalDate releaseDate) {
}
