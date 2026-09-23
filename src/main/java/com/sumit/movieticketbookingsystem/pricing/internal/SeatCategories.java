package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.catalog.SeatCategoryInfo;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates between the category codes admins use and the ids stored in the price tables.
 */
@Component
class SeatCategories {

    private final CatalogApi catalog;

    SeatCategories(CatalogApi catalog) {
        this.catalog = catalog;
    }

    /** Same prices keyed by category id; an unknown code is a validation error. */
    Map<Long, Long> byId(Map<String, Long> pricesByCode) {
        Map<String, Long> idsByCode = new LinkedHashMap<>();
        catalog.seatCategories().forEach(category -> idsByCode.put(category.code(), category.categoryId()));

        Map<Long, Long> pricesById = new LinkedHashMap<>();
        pricesByCode.forEach((code, price) -> {
            Long id = idsByCode.get(code);
            if (id == null) {
                throw new ValidationException("Unknown seat category: " + code);
            }
            pricesById.put(id, price);
        });
        return pricesById;
    }

    String codeOf(long categoryId) {
        return catalog.seatCategories().stream()
                .filter(category -> category.categoryId() == categoryId)
                .map(SeatCategoryInfo::code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No seat category " + categoryId));
    }
}
