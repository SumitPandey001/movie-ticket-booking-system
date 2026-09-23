package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.LayoutSeat;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.LayoutStatus;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatLayout;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatType;

import java.util.List;

record LayoutResponse(long id, long screenId, int version, LayoutStatus status, int gridRows, int gridCols,
                      int totalSeats, List<SeatResponse> seats) {

    static LayoutResponse from(SeatLayout layout) {
        return new LayoutResponse(layout.getId(), layout.getScreenId(), layout.getVersion(), layout.getStatus(),
                layout.getGridRows(), layout.getGridCols(), layout.getTotalSeats(),
                layout.getSeats().stream().map(SeatResponse::from).toList());
    }

    record SeatResponse(long id, String label, int row, int col, String category, SeatType type) {

        static SeatResponse from(LayoutSeat seat) {
            return new SeatResponse(seat.getId(), seat.getLabel(), seat.getGridRow(), seat.getGridCol(),
                    seat.getCategory().getCode(), seat.getSeatType());
        }
    }
}
