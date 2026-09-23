package com.sumit.movieticketbookingsystem.catalog;

/**
 * @param totalSeats sellable seats, i.e. not blocked
 */
public record LayoutView(long layoutId, long screenId, int totalSeats) {
}
