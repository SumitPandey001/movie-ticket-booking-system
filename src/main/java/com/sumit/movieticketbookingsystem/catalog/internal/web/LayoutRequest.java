package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.LayoutPlan;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatLayoutBuilder;
import com.sumit.movieticketbookingsystem.shared.SeatRef;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * { "rows": [ { "label": "A", "segments": [ { "from": 1, "to": 8, "category": "REGULAR" },
 *                                           { "aisle": 2 },
 *                                           { "from": 9, "to": 16, "category": "REGULAR" } ] } ],
 *   "blocked": ["A5"], "wheelchair": ["A1"] }
 */
record LayoutRequest(
        @NotEmpty List<@Valid Row> rows,
        List<String> blocked,
        List<String> wheelchair) {

    record Row(@NotBlank String label, @NotEmpty List<Segment> segments) {
    }

    record Segment(Integer from, Integer to, String category, Integer aisle) {
    }

    LayoutPlan toPlan() {
        SeatLayoutBuilder builder = SeatLayoutBuilder.layout();
        for (Row row : rows) {
            builder.row(row.label());
            for (Segment segment : row.segments()) {
                boolean isAisle = segment.aisle() != null;
                boolean isSeats = segment.from() != null && segment.to() != null;
                if (isAisle == isSeats) {
                    throw new ValidationException("Row " + row.label()
                            + ": each segment needs either from/to/category or aisle");
                }
                if (isAisle) {
                    builder.aisle(segment.aisle());
                } else {
                    builder.seats(segment.from(), segment.to(), segment.category());
                }
            }
        }
        seatRefs(blocked).forEach(builder::block);
        seatRefs(wheelchair).forEach(builder::wheelchair);
        return builder.build();
    }

    private static List<SeatRef> seatRefs(List<String> labels) {
        if (labels == null) {
            return List.of();
        }
        try {
            return labels.stream().map(SeatRef::parse).toList();
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
    }
}
