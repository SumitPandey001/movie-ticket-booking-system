package com.sumit.movieticketbookingsystem.pricing.internal;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(10)
class BaseTierPriceRule implements PricingRule {

    private final PriceRepository prices;

    BaseTierPriceRule(PriceRepository prices) {
        this.prices = prices;
    }

    @Override
    public void apply(PricingContext context) {
        Map<Long, Long> byCategory = prices.showPrices(context.show().showId());
        for (PricingContext.Line line : context.lines()) {
            Long tier = byCategory.get(line.categoryId);
            if (tier == null) {
                // every category of the layout gets a price when the show is created
                throw new IllegalStateException("Show " + context.show().showId() + " has no price for category "
                        + line.categoryId);
            }
            line.tier = tier;
        }
    }
}
