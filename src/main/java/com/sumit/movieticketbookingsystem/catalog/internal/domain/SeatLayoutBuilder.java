package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.SeatRef;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes a screen's seats row by row, front to back, and left to right within a row:
 * <pre>
 * SeatLayoutBuilder.layout()
 *         .row("A").seats(1, 8, "REGULAR").aisle(2).seats(9, 16, "REGULAR")
 *         .row("J").aisle(2).seats(1, 6, "RECLINER")
 *         .block(SeatRef.parse("A5"))
 *         .wheelchair(SeatRef.parse("J1"))
 *         .build();
 * </pre>
 * Seats are placed on the grid in the order they're added, so two seats can never share a cell.
 * Seat ranges and aisles are checked as they're added; the layout as a whole in {@link #build()}.
 */
public final class SeatLayoutBuilder {

    private final List<RowDraft> rows = new ArrayList<>();
    private final Set<SeatRef> blocked = new LinkedHashSet<>();
    private final Set<SeatRef> wheelchair = new LinkedHashSet<>();

    private SeatLayoutBuilder() {
    }

    public static SeatLayoutBuilder layout() {
        return new SeatLayoutBuilder();
    }

    public SeatLayoutBuilder row(String label) {
        rows.add(new RowDraft(label));
        return this;
    }

    public SeatLayoutBuilder seats(int from, int to, String categoryCode) {
        currentRow().segments.add(new Seats(from, to, categoryCode));
        return this;
    }

    public SeatLayoutBuilder aisle(int width) {
        currentRow().segments.add(new Aisle(width));
        return this;
    }

    public SeatLayoutBuilder block(SeatRef seat) {
        blocked.add(seat);
        return this;
    }

    public SeatLayoutBuilder wheelchair(SeatRef seat) {
        wheelchair.add(seat);
        return this;
    }

    public LayoutPlan build() {
        if (rows.isEmpty()) {
            throw new ValidationException("A layout needs at least one row");
        }
        Set<SeatRef> both = new HashSet<>(blocked);
        both.retainAll(wheelchair);
        if (!both.isEmpty()) {
            throw new ValidationException("Seats can't be both blocked and wheelchair: " + labels(both));
        }

        Map<SeatRef, LayoutPlan.Seat> seats = new LinkedHashMap<>();
        Set<String> rowLabels = new HashSet<>();
        int gridCols = 0;
        for (int i = 0; i < rows.size(); i++) {
            RowDraft row = rows.get(i);
            if (!rowLabels.add(row.label)) {
                throw new ValidationException("Row " + row.label + " appears more than once");
            }
            int col = 1;
            for (Segment segment : row.segments) {
                switch (segment) {
                    case Aisle aisle -> col += aisle.width();
                    case Seats run -> {
                        for (int number = run.from(); number <= run.to(); number++) {
                            SeatRef ref = seatRef(row.label, number);
                            LayoutPlan.Seat seat = new LayoutPlan.Seat(ref, i + 1, col++, run.categoryCode(), typeOf(ref));
                            if (seats.putIfAbsent(ref, seat) != null) {
                                throw new ValidationException("Seat " + ref.label() + " appears more than once");
                            }
                        }
                    }
                }
            }
            if (row.segments.stream().noneMatch(Seats.class::isInstance)) {
                throw new ValidationException("Row " + row.label + " has no seats");
            }
            gridCols = Math.max(gridCols, col - 1);
        }

        Set<SeatRef> unknown = new LinkedHashSet<>(blocked);
        unknown.addAll(wheelchair);
        unknown.removeAll(seats.keySet());
        if (!unknown.isEmpty()) {
            throw new ValidationException("Not seats in this layout: " + labels(unknown));
        }

        LayoutPlan plan = new LayoutPlan(rows.size(), gridCols, new ArrayList<>(seats.values()));
        if (plan.sellableSeats() == 0) {
            throw new ValidationException("A layout needs at least one seat that isn't blocked");
        }
        return plan;
    }

    private RowDraft currentRow() {
        if (rows.isEmpty()) {
            throw new IllegalStateException("Call row() before adding seats or aisles");
        }
        return rows.getLast();
    }

    private SeatType typeOf(SeatRef ref) {
        if (blocked.contains(ref)) {
            return SeatType.BLOCKED;
        }
        return wheelchair.contains(ref) ? SeatType.WHEELCHAIR : SeatType.NORMAL;
    }

    private static SeatRef seatRef(String row, int number) {
        try {
            return new SeatRef(row, number);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
    }

    private static String labels(Set<SeatRef> refs) {
        return refs.stream().map(SeatRef::label).collect(Collectors.joining(", "));
    }

    private static final class RowDraft {
        private final String label;
        private final List<Segment> segments = new ArrayList<>();

        private RowDraft(String label) {
            this.label = label;
        }
    }

    private sealed interface Segment permits Seats, Aisle {
    }

    private record Seats(int from, int to, String categoryCode) implements Segment {
        Seats {
            if (from < 1 || to < from) {
                throw new ValidationException("Invalid seat range " + from + "-" + to);
            }
            if (categoryCode == null || categoryCode.isBlank()) {
                throw new ValidationException("Seats " + from + "-" + to + " need a category");
            }
        }
    }

    private record Aisle(int width) implements Segment {
        Aisle {
            if (width < 1) {
                throw new ValidationException("Aisle width must be at least 1");
            }
        }
    }
}
