package com.sumit.movieticketbookingsystem.catalog;

import java.util.List;

public record LayoutView(long layoutId, long screenId, int gridRows, int gridCols, int totalSeats, List<Seat> seats) {

    public LayoutView {
        seats = List.copyOf(seats);
    }

    public record Seat(long layoutSeatId, String label, long categoryId, int gridRow, int gridCol, String type) {
        public boolean blocked() {
            return "BLOCKED".equals(type);
        }
    }
}
