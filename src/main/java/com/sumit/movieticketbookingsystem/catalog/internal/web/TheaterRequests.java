package com.sumit.movieticketbookingsystem.catalog.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class TheaterRequests {

    private TheaterRequests() {
    }

    record Create(
            @NotNull Long cityId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String area,
            @Size(max = 300) String address) {
    }

    // no cityId: a theater never moves to another city
    record Update(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String area,
            @Size(max = 300) String address) {
    }

    record ScreenName(@NotBlank @Size(max = 40) String name) {
    }
}
