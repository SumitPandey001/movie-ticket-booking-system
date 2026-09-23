package com.sumit.movieticketbookingsystem.catalog;

import java.util.List;

/**
 * A seat layout version. Never changes once active, so callers may copy from it freely.
 *
 * @param totalSeats sellable seats, i.e. not blocked
 */
public record LayoutView(long layoutId, long screenId, int totalSeats, List<Seat> seats) {

    public LayoutView {
        seats = List.copyOf(seats);
    }

    /**
     * @param blocked blocked in the layout itself, so never sellable for any show
     */
    public record Seat(long layoutSeatId, String label, long categoryId, boolean blocked) {
    }
}
