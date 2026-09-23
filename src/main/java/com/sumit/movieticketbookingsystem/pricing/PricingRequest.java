package com.sumit.movieticketbookingsystem.pricing;

import java.util.List;
import java.util.UUID;

/**
 * @param couponCode optional; null when the customer hasn't entered one
 */
public record PricingRequest(ShowPricing show, UUID userId, List<SeatToPrice> seats, String couponCode) {

    public PricingRequest {
        seats = List.copyOf(seats);
    }
}
