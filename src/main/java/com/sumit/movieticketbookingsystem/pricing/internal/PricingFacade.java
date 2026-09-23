package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.PriceQuote;
import com.sumit.movieticketbookingsystem.pricing.PricingApi;
import com.sumit.movieticketbookingsystem.pricing.SeatToPrice;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
class PricingFacade implements PricingApi {

    private final PriceRepository prices;
    private final SeatCategories categories;
    private final PriceCalculator calculator;

    PricingFacade(PriceRepository prices, SeatCategories categories, PriceCalculator calculator) {
        this.prices = prices;
        this.categories = categories;
        this.calculator = calculator;
    }

    @Override
    public long initializeShowPrices(long showId, long theaterId, Set<Long> categoryIds, Map<String, Long> overrides) {
        Map<Long, Long> overrideById = categories.byId(overrides);
        for (long categoryId : overrideById.keySet()) {
            if (!categoryIds.contains(categoryId)) {
                throw new ValidationException("The show has no " + categories.codeOf(categoryId) + " seats");
            }
        }

        Map<Long, Long> defaults = prices.theaterPrices(theaterId);
        for (long categoryId : categoryIds) {
            Long override = overrideById.get(categoryId);
            Long price = override != null ? override : defaults.get(categoryId);
            if (price == null) {
                throw new ValidationException("Theater " + theaterId + " has no price for "
                        + categories.codeOf(categoryId) + " seats");
            }
            prices.insertShowPrice(showId, categoryId, price, override != null);
        }
        return prices.lowestShowPrice(showId);
    }

    @Override
    public long overrideShowPrices(long showId, Map<String, Long> newPrices) {
        categories.byId(newPrices).forEach((categoryId, price) -> {
            if (!prices.overrideShowPrice(showId, categoryId, price)) {
                throw new ValidationException("Show " + showId + " has no " + categories.codeOf(categoryId) + " seats");
            }
        });
        return prices.lowestShowPrice(showId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> showPrices(long showId) {
        return prices.showPrices(showId);
    }

    @Override
    @Transactional(readOnly = true)
    public PriceQuote quote(long showId, List<SeatToPrice> seats) {
        return calculator.quote(showId, seats);
    }
}
