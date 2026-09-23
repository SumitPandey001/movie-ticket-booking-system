package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.SeatPriceLine;
import com.sumit.movieticketbookingsystem.pricing.SeatToPrice;
import com.sumit.movieticketbookingsystem.pricing.ShowPricing;

import java.util.List;

/**
 * The quote while the pricing rules work on it: one mutable line per seat, filled in rule by rule.
 */
final class PricingContext {

    private final ShowPricing show;
    private final List<Line> lines;
    private String dayRule;

    PricingContext(ShowPricing show, List<SeatToPrice> seats) {
        this.show = show;
        this.lines = seats.stream().map(seat -> new Line(seat.layoutSeatId(), seat.categoryId())).toList();
    }

    ShowPricing show() {
        return show;
    }

    List<Line> lines() {
        return lines;
    }

    String dayRule() {
        return dayRule;
    }

    void dayRule(String name) {
        this.dayRule = name;
    }

    static final class Line {
        final long layoutSeatId;
        final long categoryId;
        long tier;
        long dayAdjustment;
        long discount;
        long fee;
        long ticketTax;
        long feeTax;

        private Line(long layoutSeatId, long categoryId) {
            this.layoutSeatId = layoutSeatId;
            this.categoryId = categoryId;
        }

        long base() {
            return tier + dayAdjustment;
        }

        SeatPriceLine toPriceLine() {
            return new SeatPriceLine(layoutSeatId, categoryId, tier, dayAdjustment, discount, fee, ticketTax, feeTax);
        }
    }
}
