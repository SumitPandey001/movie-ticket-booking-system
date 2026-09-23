package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.SeatPriceLine;
import com.sumit.movieticketbookingsystem.pricing.SeatToPrice;

import java.util.List;

/**
 * The quote while the pricing rules work on it: one mutable line per seat, filled in rule by rule.
 */
final class PricingContext {

    private final long showId;
    private final List<Line> lines;

    PricingContext(long showId, List<SeatToPrice> seats) {
        this.showId = showId;
        this.lines = seats.stream().map(seat -> new Line(seat.layoutSeatId(), seat.categoryId())).toList();
    }

    long showId() {
        return showId;
    }

    List<Line> lines() {
        return lines;
    }

    static final class Line {
        final long layoutSeatId;
        final long categoryId;
        long tier;
        long discount;
        long fee;
        long ticketTax;
        long feeTax;

        private Line(long layoutSeatId, long categoryId) {
            this.layoutSeatId = layoutSeatId;
            this.categoryId = categoryId;
        }

        SeatPriceLine toPriceLine() {
            return new SeatPriceLine(layoutSeatId, categoryId, tier, discount, fee, ticketTax, feeTax);
        }
    }
}
