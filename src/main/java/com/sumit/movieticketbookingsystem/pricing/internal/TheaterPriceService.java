package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.persistence.ConstraintViolations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TheaterPriceService {

    private static final Logger log = LoggerFactory.getLogger(TheaterPriceService.class);

    private final PriceRepository prices;
    private final SeatCategories categories;

    TheaterPriceService(PriceRepository prices, SeatCategories categories) {
        this.prices = prices;
        this.categories = categories;
    }

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
        log.info("Theater {} prices set: {}", theaterId, pricesByCode);
        return prices(theaterId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> prices(long theaterId) {
        Map<String, Long> byCode = new LinkedHashMap<>();
        prices.theaterPrices(theaterId)
                .forEach((categoryId, price) -> byCode.put(categories.codeOf(categoryId), price));
        return byCode;
    }
}
