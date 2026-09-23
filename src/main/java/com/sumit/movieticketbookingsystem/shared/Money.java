package com.sumit.movieticketbookingsystem.shared;

/**
 * An amount in paise. Kept as a long so there is never any floating point in money math.
 */
public record Money(long paise) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (paise < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + paise);
        }
    }

    public static Money ofPaise(long paise) {
        return new Money(paise);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(paise, other.paise));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(paise, other.paise));
    }

    /**
     * Percentage of this amount, rounded half-up to the nearest paisa.
     */
    public Money percent(int percent) {
        if (percent < 0) {
            throw new IllegalArgumentException("Percent cannot be negative: " + percent);
        }
        long scaled = Math.multiplyExact(paise, percent);
        return new Money(Math.addExact(scaled, 50) / 100);
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    /** For customer-facing text: ₹300, or ₹837.80 when there are paise. */
    public String inRupees() {
        return paise % 100 == 0 ? "₹" + paise / 100 : "₹%d.%02d".formatted(paise / 100, paise % 100);
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(paise, other.paise);
    }
}
