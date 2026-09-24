package com.sumit.movieticketbookingsystem.pricing.internal.web;

import com.sumit.movieticketbookingsystem.pricing.internal.TheaterPriceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/theaters/{theaterId}/prices")
class TheaterPriceAdminController {

    private final TheaterPriceService priceService;

    TheaterPriceAdminController(TheaterPriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping
    TheaterPricesResponse prices(@PathVariable long theaterId) {
        return new TheaterPricesResponse(priceService.prices(theaterId));
    }

    @PutMapping
    TheaterPricesResponse setPrices(@PathVariable long theaterId, @Valid @RequestBody TheaterPricesRequest request) {
        return new TheaterPricesResponse(priceService.setPrices(theaterId, request.prices()));
    }
}
