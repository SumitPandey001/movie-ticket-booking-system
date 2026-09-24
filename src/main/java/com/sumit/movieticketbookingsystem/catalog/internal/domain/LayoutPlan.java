package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.SeatRef;

import java.util.List;

public record LayoutPlan(int gridRows, int gridCols, List<Seat> seats) {

    public LayoutPlan {
        seats = List.copyOf(seats);
    }

    public long sellableSeats() {
        return seats.stream().filter(seat -> seat.type() != SeatType.BLOCKED).count();
    }

    public record Seat(SeatRef ref, int gridRow, int gridCol, String categoryCode, SeatType type) {
    }
}
