package com.sumit.movieticketbookingsystem.pricing.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/theaters/{theaterId}/prices")
class TheaterPriceAdminController {

    private final TheaterPriceService priceService;

    TheaterPriceAdminController(TheaterPriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping
    TheaterPrices prices(@PathVariable long theaterId) {
        return new TheaterPrices(priceService.prices(theaterId));
    }

    @PutMapping
    TheaterPrices setPrices(@PathVariable long theaterId, @Valid @RequestBody TheaterPrices request) {
        return new TheaterPrices(priceService.setPrices(theaterId, request.prices()));
    }

    /** Price in paise by category code, e.g. {@code {"prices": {"REGULAR": 30000, "PREMIUM": 36000}}}. */
    record TheaterPrices(@NotEmpty Map<@NotBlank String, @NotNull @Positive Long> prices) {
    }
}
