package com.sumit.movieticketbookingsystem.catalog;

import java.util.List;

/**
 * A seat layout version. Never changes once active, so callers may copy from it freely.
 *
 * @param totalSeats sellable seats, i.e. not blocked
 */
public record LayoutView(long layoutId, long screenId, int gridRows, int gridCols, int totalSeats, List<Seat> seats) {

    public LayoutView {
        seats = List.copyOf(seats);
    }

    /**
     * @param gridRow 1-based, front row first
     * @param gridCol 1-based, left to right; empty cells are aisles
     * @param type    NORMAL, WHEELCHAIR or BLOCKED
     */
    public record Seat(long layoutSeatId, String label, long categoryId, int gridRow, int gridCol, String type) {

        /** Blocked in the layout itself, so never sellable unless an admin unblocks it for a show. */
        public boolean blocked() {
            return "BLOCKED".equals(type);
        }
    }
}
