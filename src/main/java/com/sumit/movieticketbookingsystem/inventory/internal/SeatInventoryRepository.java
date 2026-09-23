package com.sumit.movieticketbookingsystem.inventory.internal;

import com.sumit.movieticketbookingsystem.catalog.LayoutView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
class SeatInventoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    SeatInventoryRepository(JdbcTemplate jdbcTemplate, JdbcClient jdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
    }

    void insertSeats(long showId, List<LayoutView.Seat> seats) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO show_seat (show_id, layout_seat_id, seat_label, category_id, status)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                seats.stream()
                        .map(seat -> new Object[] {showId, seat.layoutSeatId(), seat.label(), seat.categoryId(),
                                seat.blocked() ? "BLOCKED" : "AVAILABLE"})
                        .toList());
    }

    /** Moves the given seats from one status to another and returns the ids that actually changed. */
    List<Long> changeStatus(long showId, Set<Long> seatIds, String from, String to) {
        if (seatIds.isEmpty()) {
            return List.of();   // IN () isn't valid SQL
        }
        return jdbc.sql("""
                        UPDATE show_seat SET status = :to, version = version + 1
                        WHERE show_id = :showId AND layout_seat_id IN (:seatIds) AND status = :from
                        RETURNING layout_seat_id
                        """)
                .param("showId", showId)
                .param("seatIds", seatIds)
                .param("from", from)
                .param("to", to)
                .query(Long.class)
                .list();
    }
}
