package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.persistence.ConstraintViolations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
class TheaterPriceService {

    private final PriceRepository prices;
    private final SeatCategories categories;

    TheaterPriceService(PriceRepository prices, SeatCategories categories) {
        this.prices = prices;
        this.categories = categories;
    }

    /** Sets the listed categories' defaults; categories not listed keep their current price. */
    @Transactional
    public Map<String, Long> setPrices(long theaterId, Map<String, Long> pricesByCode) {
        try {
            categories.byId(pricesByCode).forEach((categoryId, price) ->
                    prices.upsertTheaterPrice(theaterId, categoryId, price));
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, "theater_category_price_theater_fk")) {
                throw new NotFoundException("Theater", theaterId);
            }
            throw e;
        }
        return prices(theaterId);
    }

    /** Price in paise by category code. */
    @Transactional(readOnly = true)
    public Map<String, Long> prices(long theaterId) {
        Map<String, Long> byCode = new LinkedHashMap<>();
        prices.theaterPrices(theaterId).forEach((categoryId, price) -> byCode.put(categories.codeOf(categoryId), price));
        return byCode;
    }
}
