package com.sumit.movieticketbookingsystem.inventory.internal;

import com.sumit.movieticketbookingsystem.catalog.LayoutView;
import com.sumit.movieticketbookingsystem.inventory.SeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    /**
     * Locks the requested rows in seat order without waiting (NOWAIT raises 55P03 if another transaction has
     * any of them), then claims the ones that are free. Returns the claimed seats with the status each had.
     */
    List<ClaimedSeat> hold(long showId, Set<Long> seatIds, UUID bookingId, Instant expiresAt, Instant now) {
        return jdbc.sql("""
                        WITH requested AS (
                            SELECT show_id, layout_seat_id, status, hold_expires_at
                            FROM show_seat
                            WHERE show_id = :showId AND layout_seat_id IN (:seatIds)
                            ORDER BY layout_seat_id
                            FOR UPDATE NOWAIT
                        )
                        UPDATE show_seat s
                        SET status = 'HELD', booking_id = :bookingId, hold_expires_at = :expiresAt,
                            version = s.version + 1
                        FROM requested r
                        WHERE s.show_id = r.show_id AND s.layout_seat_id = r.layout_seat_id
                          AND (r.status = 'AVAILABLE' OR (r.status = 'HELD' AND r.hold_expires_at < :now))
                        RETURNING s.layout_seat_id, s.seat_label, s.category_id, r.status AS previous_status
                        """)
                .param("showId", showId)
                .param("seatIds", seatIds)
                .param("bookingId", bookingId)
                .param("expiresAt", utc(expiresAt))
                .param("now", utc(now))
                .query((rs, row) -> new ClaimedSeat(rs.getLong("layout_seat_id"), rs.getString("seat_label"),
                        rs.getLong("category_id"), SeatStatus.valueOf(rs.getString("previous_status"))))
                .list();
    }

    /** Returns the seats that became BOOKED, with the status each had. */
    List<ClaimedSeat> confirm(long showId, Set<Long> seatIds, UUID bookingId, Instant now) {
        return jdbc.sql("""
                        WITH requested AS (
                            SELECT show_id, layout_seat_id, status, booking_id, hold_expires_at
                            FROM show_seat
                            WHERE show_id = :showId AND layout_seat_id IN (:seatIds)
                            ORDER BY layout_seat_id
                            FOR UPDATE
                        )
                        UPDATE show_seat s
                        SET status = 'BOOKED', booking_id = :bookingId, hold_expires_at = NULL, version = s.version + 1
                        FROM requested r
                        WHERE s.show_id = r.show_id AND s.layout_seat_id = r.layout_seat_id
                          AND (   (r.status = 'HELD' AND r.booking_id = :bookingId)
                               OR  r.status = 'AVAILABLE'
                               OR (r.status = 'HELD' AND r.hold_expires_at < :now))
                        RETURNING s.layout_seat_id, s.seat_label, s.category_id, r.status AS previous_status
                        """)
                .param("showId", showId)
                .param("seatIds", seatIds)
                .param("bookingId", bookingId)
                .param("now", utc(now))
                .query((rs, row) -> new ClaimedSeat(rs.getLong("layout_seat_id"), rs.getString("seat_label"),
                        rs.getLong("category_id"), SeatStatus.valueOf(rs.getString("previous_status"))))
                .list();
    }

    /** Frees the seats this booking still holds; seats already taken over by someone else are left alone. */
    int releaseHeld(long showId, UUID bookingId) {
        return jdbc.sql("""
                        UPDATE show_seat
                        SET status = 'AVAILABLE', booking_id = NULL, hold_expires_at = NULL, version = version + 1
                        WHERE show_id = ? AND booking_id = ? AND status = 'HELD'
                        """)
                .params(showId, bookingId)
                .update();
    }

    /** Seats left per show, counting holds that ran out before {@code now} as available. */
    Map<Long, Integer> availableCounts(Collection<Long> showIds, Instant now) {
        Map<Long, Integer> counts = new HashMap<>();
        if (showIds.isEmpty()) {
            return counts;
        }
        jdbc.sql("""
                        SELECT show_id,
                               count(*) FILTER (WHERE status = 'AVAILABLE'
                                                   OR (status = 'HELD' AND hold_expires_at < :now)) AS available
                        FROM show_seat WHERE show_id IN (:showIds) GROUP BY show_id
                        """)
                .param("showIds", showIds)
                .param("now", utc(now))
                .query(rs -> {
                    counts.put(rs.getLong("show_id"), rs.getInt("available"));
                });
        return counts;
    }

    Map<Long, SeatStatus> statuses(long showId, Instant now) {
        Map<Long, SeatStatus> statuses = new HashMap<>();
        jdbc.sql("""
                        SELECT layout_seat_id,
                               CASE WHEN status = 'HELD' AND hold_expires_at < :now THEN 'AVAILABLE' ELSE status END
                                   AS status
                        FROM show_seat WHERE show_id = :showId
                        """)
                .param("showId", showId)
                .param("now", utc(now))
                .query(rs -> {
                    statuses.put(rs.getLong("layout_seat_id"), SeatStatus.valueOf(rs.getString("status")));
                });
        return statuses;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    record ClaimedSeat(long layoutSeatId, String label, long categoryId, SeatStatus previousStatus) {
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
