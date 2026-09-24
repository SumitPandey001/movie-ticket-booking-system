package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.SeatRef;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class LayoutSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layout_id")
    private SeatLayout layout;

    private String rowLabel;

    private int seatNumber;

    private int gridRow;

    private int gridCol;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private SeatCategory category;

    @Enumerated(EnumType.STRING)
    private SeatType seatType;

    protected LayoutSeat() {
    }

    LayoutSeat(SeatLayout layout, LayoutPlan.Seat seat, SeatCategory category) {
        this.layout = layout;
        this.rowLabel = seat.ref().rowLabel();
        this.seatNumber = seat.ref().seatNumber();
        this.gridRow = seat.gridRow();
        this.gridCol = seat.gridCol();
        this.category = category;
        this.seatType = seat.type();
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return new SeatRef(rowLabel, seatNumber).label();
    }

    public int getGridRow() {
        return gridRow;
    }

    public int getGridCol() {
        return gridCol;
    }

    public SeatCategory getCategory() {
        return category;
    }

    public SeatType getSeatType() {
        return seatType;
    }
}
