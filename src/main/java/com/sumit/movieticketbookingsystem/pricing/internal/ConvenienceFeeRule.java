package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.shared.BookingProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
class ConvenienceFeeRule implements PricingRule {

    private final long feePaise;

    ConvenienceFeeRule(BookingProperties properties) {
        this.feePaise = properties.convenienceFeePaise();
    }

    @Override
    public void apply(PricingContext context) {
        context.lines().forEach(line -> line.fee = feePaise);
    }
}
