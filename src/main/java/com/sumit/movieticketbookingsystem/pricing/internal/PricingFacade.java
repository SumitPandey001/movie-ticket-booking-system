package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.PriceQuote;
import com.sumit.movieticketbookingsystem.pricing.PricingApi;
import com.sumit.movieticketbookingsystem.pricing.PricingRequest;
import com.sumit.movieticketbookingsystem.pricing.ShowPricing;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
class PricingFacade implements PricingApi {

    private final PriceRepository prices;
    private final DayRuleRepository dayRules;
    private final SeatCategories categories;
    private final PriceCalculator calculator;

    PricingFacade(PriceRepository prices, DayRuleRepository dayRules, SeatCategories categories,
            PriceCalculator calculator) {
        this.prices = prices;
        this.dayRules = dayRules;
        this.categories = categories;
        this.calculator = calculator;
    }

    @Override
    public long initializeShowPrices(ShowPricing show, Set<Long> categoryIds, Map<String, Long> overrides) {
        Map<Long, Long> overrideById = categories.byId(overrides);
        for (long categoryId : overrideById.keySet()) {
            if (!categoryIds.contains(categoryId)) {
                throw new ValidationException("The show has no " + categories.codeOf(categoryId) + " seats");
            }
        }

        Map<Long, Long> defaults = prices.theaterPrices(show.theaterId());
        for (long categoryId : categoryIds) {
            Long override = overrideById.get(categoryId);
            Long price = override != null ? override : defaults.get(categoryId);
            if (price == null) {
                throw new ValidationException("Theater " + show.theaterId() + " has no price for "
                        + categories.codeOf(categoryId) + " seats");
            }
            prices.insertShowPrice(show.showId(), categoryId, price, override != null);
        }
        return lowestDisplayPrice(show);
    }

    @Override
    public long overrideShowPrices(ShowPricing show, Map<String, Long> newPrices) {
        categories.byId(newPrices).forEach((categoryId, price) -> {
            if (!prices.overrideShowPrice(show.showId(), categoryId, price)) {
                throw new ValidationException("Show " + show.showId() + " has no "
                        + categories.codeOf(categoryId) + " seats");
            }
        });
        return lowestDisplayPrice(show);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> displayPrices(ShowPricing show) {
        Optional<DayRule> dayRule = dayRules.ruleFor(show);
        Map<Long, Long> display = new LinkedHashMap<>();
        prices.showPrices(show.showId()).forEach((categoryId, tier) ->
                display.put(categoryId, tier + dayRule.map(rule -> rule.adjustment(tier)).orElse(0L)));
        return display;
    }

    @Override
    @Transactional(readOnly = true)
    public PriceQuote quote(PricingRequest request) {
        return calculator.quote(request);
    }

    // price_from is worked out when a show's prices are set, so a pricing rule added or changed later doesn't
    // reach shows that already exist (quotes and the seat map always use the current rules). If admins start
    // editing rules often, refresh price_from for the shows a rule affects.
    private long lowestDisplayPrice(ShowPricing show) {
        return displayPrices(show).values().stream().mapToLong(Long::longValue).min()
                .orElseThrow(() -> new IllegalStateException("Show " + show.showId() + " has no prices"));
    }
}
