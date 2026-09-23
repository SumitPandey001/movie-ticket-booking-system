package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.SeatRef;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.junit.jupiter.api.Test;

import static com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatLayoutBuilder.layout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatLayoutBuilderTest {

    @Test
    void placesSeatsLeftToRightAndLeavesGapsForAisles() {
        LayoutPlan plan = layout()
                .row("A").seats(1, 4, "REGULAR").aisle(2).seats(5, 8, "REGULAR")
                .row("B").aisle(1).seats(1, 3, "PREMIUM")
                .build();

        assertThat(plan.gridRows()).isEqualTo(2);
        assertThat(plan.gridCols()).isEqualTo(10);
        assertThat(seat(plan, "A4")).extracting(LayoutPlan.Seat::gridRow, LayoutPlan.Seat::gridCol).containsExactly(1, 4);
        assertThat(seat(plan, "A5").gridCol()).isEqualTo(7);   // columns 5 and 6 are the aisle
        assertThat(seat(plan, "B1")).extracting(LayoutPlan.Seat::gridRow, LayoutPlan.Seat::gridCol).containsExactly(2, 2);
        assertThat(seat(plan, "B1").categoryCode()).isEqualTo("PREMIUM");
        assertThat(plan.seats()).hasSize(11);
    }

    @Test
    void blockedAndWheelchairSeats() {
        LayoutPlan plan = layout()
                .row("A").seats(1, 4, "REGULAR")
                .block(SeatRef.parse("A2"))
                .wheelchair(SeatRef.parse("A4"))
                .build();

        assertThat(seat(plan, "A1").type()).isEqualTo(SeatType.NORMAL);
        assertThat(seat(plan, "A2").type()).isEqualTo(SeatType.BLOCKED);
        assertThat(seat(plan, "A4").type()).isEqualTo(SeatType.WHEELCHAIR);
        assertThat(plan.sellableSeats()).isEqualTo(3);
    }

    @Test
    void rejectsDuplicateSeatsAndRows() {
        assertInvalid(layout().row("A").seats(1, 4, "REGULAR").aisle(1).seats(4, 6, "REGULAR"),
                "Seat A4 appears more than once");
        assertInvalid(layout().row("A").seats(1, 2, "REGULAR").row("A").seats(3, 4, "REGULAR"),
                "Row A appears more than once");
    }

    @Test
    void rejectsLayoutsWithNothingToSell() {
        assertInvalid(layout(), "A layout needs at least one row");
        assertInvalid(layout().row("A").aisle(3), "Row A has no seats");
        assertInvalid(layout().row("A").seats(1, 2, "REGULAR").block(SeatRef.parse("A1")).block(SeatRef.parse("A2")),
                "A layout needs at least one seat that isn't blocked");
    }

    @Test
    void blockedOrWheelchairSeatsMustExistAndNotOverlap() {
        assertInvalid(layout().row("A").seats(1, 2, "REGULAR").block(SeatRef.parse("B1")),
                "Not seats in this layout: B1");
        assertInvalid(layout().row("A").seats(1, 2, "REGULAR")
                        .block(SeatRef.parse("A1")).wheelchair(SeatRef.parse("A1")),
                "Seats can't be both blocked and wheelchair: A1");
    }

    @Test
    void rejectsBadSegmentsAndRowLabels() {
        assertThatThrownBy(() -> layout().row("A").seats(5, 2, "REGULAR"))
                .isInstanceOf(ValidationException.class).hasMessage("Invalid seat range 5-2");
        assertThatThrownBy(() -> layout().row("A").seats(1, 2, " "))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> layout().row("A").aisle(0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> layout().row("a1").seats(1, 2, "REGULAR").build())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void seatsNeedARowFirst() {
        assertThatThrownBy(() -> layout().seats(1, 2, "REGULAR")).isInstanceOf(IllegalStateException.class);
    }

    private static LayoutPlan.Seat seat(LayoutPlan plan, String label) {
        return plan.seats().stream()
                .filter(seat -> seat.ref().label().equals(label))
                .findFirst()
                .orElseThrow();
    }

    private static void assertInvalid(SeatLayoutBuilder builder, String message) {
        assertThatThrownBy(builder::build).isInstanceOf(ValidationException.class).hasMessage(message);
    }
}
