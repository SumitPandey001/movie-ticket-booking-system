package com.sumit.movieticketbookingsystem.pricing.internal;

import com.sumit.movieticketbookingsystem.shared.Money;

/**
 * The day-of-week surcharge that applies to a show.
 */
record DayRule(String name, AdjustmentType type, long value) {

    /** Paise added to one seat's tier price. */
    long adjustment(long tierPaise) {
        return switch (type) {
            case PERCENT -> Money.ofPaise(tierPaise).percent(Math.toIntExact(value)).paise();
            case FLAT -> value;
        };
    }
}
