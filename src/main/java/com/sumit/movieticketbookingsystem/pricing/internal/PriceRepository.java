package com.sumit.movieticketbookingsystem.pricing.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
class PriceRepository {

    private final JdbcClient jdbc;

    PriceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void upsertTheaterPrice(long theaterId, long categoryId, long pricePaise) {
        jdbc.sql("""
                        INSERT INTO theater_category_price (theater_id, category_id, price_paise)
                        VALUES (:theaterId, :categoryId, :price)
                        ON CONFLICT (theater_id, category_id) DO UPDATE SET price_paise = EXCLUDED.price_paise
                        """)
                .param("theaterId", theaterId)
                .param("categoryId", categoryId)
                .param("price", pricePaise)
                .update();
    }

    /** Price in paise by category id. */
    Map<Long, Long> theaterPrices(long theaterId) {
        Map<Long, Long> prices = new LinkedHashMap<>();
        jdbc.sql("SELECT category_id, price_paise FROM theater_category_price WHERE theater_id = ? ORDER BY category_id")
                .param(theaterId)
                .query(rs -> {
                    prices.put(rs.getLong("category_id"), rs.getLong("price_paise"));
                });
        return prices;
    }

    /** Price in paise by category id. */
    Map<Long, Long> showPrices(long showId) {
        Map<Long, Long> prices = new LinkedHashMap<>();
        jdbc.sql("SELECT category_id, price_paise FROM show_category_price WHERE show_id = ? ORDER BY category_id")
                .param(showId)
                .query(rs -> {
                    prices.put(rs.getLong("category_id"), rs.getLong("price_paise"));
                });
        return prices;
    }

    void insertShowPrice(long showId, long categoryId, long pricePaise, boolean overridden) {
        jdbc.sql("""
                        INSERT INTO show_category_price (show_id, category_id, price_paise, overridden)
                        VALUES (?, ?, ?, ?)
                        """)
                .params(showId, categoryId, pricePaise, overridden)
                .update();
    }

    /** @return false when the show has no seats of that category */
    boolean overrideShowPrice(long showId, long categoryId, long pricePaise) {
        return jdbc.sql("""
                        UPDATE show_category_price SET price_paise = ?, overridden = TRUE
                        WHERE show_id = ? AND category_id = ?
                        """)
                .params(pricePaise, showId, categoryId)
                .update() == 1;
    }

    long lowestShowPrice(long showId) {
        return jdbc.sql("SELECT min(price_paise) FROM show_category_price WHERE show_id = ?")
                .param(showId)
                .query(Long.class)
                .single();
    }
}
