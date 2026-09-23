package com.sumit.movieticketbookingsystem.shared;

import java.time.Instant;
import java.util.Objects;

/**
 * Half-open interval {@code [from, to)}.
 */
public record TimeWindow(Instant from, Instant to) {

    public TimeWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("Window start must be before its end: " + from + " - " + to);
        }
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(from) && instant.isBefore(to);
    }
}
