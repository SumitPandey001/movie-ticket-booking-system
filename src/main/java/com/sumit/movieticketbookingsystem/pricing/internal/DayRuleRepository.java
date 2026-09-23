package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.pricing.ShowPricing;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class DayRuleRepository {

    private final JdbcClient jdbc;

    DayRuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The one rule for this show's date: active, valid that day and covering its weekday. The most specific scope
     * wins (theater, then city, then global); between two rules at the same level the newer one wins.
     */
    Optional<DayRule> ruleFor(ShowPricing show) {
        return jdbc.sql("""
                        SELECT name, adjustment_type, adjustment_value FROM pricing_rule
                        WHERE active
                          AND CAST(:weekday AS SMALLINT) = ANY(days_of_week)
                          AND (valid_from IS NULL OR valid_from <= :date)
                          AND (valid_to IS NULL OR valid_to >= :date)
                          AND (scope_type = 'GLOBAL'
                               OR (scope_type = 'CITY' AND scope_id = :cityId)
                               OR (scope_type = 'THEATER' AND scope_id = :theaterId))
                        ORDER BY CASE scope_type WHEN 'THEATER' THEN 1 WHEN 'CITY' THEN 2 ELSE 3 END, id DESC
                        LIMIT 1
                        """)
                .param("weekday", show.showDate().getDayOfWeek().getValue())
                .param("date", show.showDate())
                .param("cityId", show.cityId())
                .param("theaterId", show.theaterId())
                .query((rs, row) -> new DayRule(rs.getString("name"),
                        AdjustmentType.valueOf(rs.getString("adjustment_type")), rs.getLong("adjustment_value")))
                .optional();
    }
}
