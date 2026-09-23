package com.sumit.movieticketbookingsystem.catalog.internal.domain;

public enum SeatType {
    NORMAL,
    WHEELCHAIR,
    /** Physically there but never sold, e.g. a broken seat or one reserved for the projectionist. */
    BLOCKED
}
