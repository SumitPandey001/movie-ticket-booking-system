package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import com.sumit.movieticketbookingsystem.shared.Money;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * GST on the ticket after discount and, separately, on the fee; each rounded half-up per seat.
 */
@Component
@Order(50)
class GstRule implements PricingRule {

    private final int gstPercent;

    GstRule(BookingProperties properties) {
        this.gstPercent = properties.gstPercent();
    }

    @Override
    public void apply(PricingContext context) {
        for (PricingContext.Line line : context.lines()) {
            line.ticketTax = Money.ofPaise(line.base() - line.discount).percent(gstPercent).paise();
            line.feeTax = Money.ofPaise(line.fee).percent(gstPercent).paise();
        }
    }
}
