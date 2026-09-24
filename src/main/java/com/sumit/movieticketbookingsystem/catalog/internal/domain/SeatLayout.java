package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.error.InvalidStateException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import com.sumit.movieticketbookingsystem.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Entity
public class SeatLayout extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long screenId;

    private int version;

    @Enumerated(EnumType.STRING)
    private LayoutStatus status;

    private int gridRows;

    private int gridCols;

    private int totalSeats;

    @OneToMany(mappedBy = "layout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("gridRow, gridCol")
    private List<LayoutSeat> seats = new ArrayList<>();

    protected SeatLayout() {
    }

    public static SeatLayout draft(long screenId, int version, LayoutPlan plan,
            Map<String, SeatCategory> categoriesByCode) {
        SeatLayout layout = new SeatLayout();
        layout.screenId = screenId;
        layout.version = version;
        layout.status = LayoutStatus.DRAFT;
        layout.addSeats(plan, categoriesByCode);
        return layout;
    }

    /**
     * First half of editing a draft. The caller must flush before addSeats, otherwise Hibernate
     * inserts the new seats before deleting the old ones and trips the unique seat-label constraint.
     */
    public void clearSeats() {
        requireDraft();
        seats.clear();
    }

    public void addSeats(LayoutPlan plan, Map<String, SeatCategory> categoriesByCode) {
        requireDraft();
        for (LayoutPlan.Seat seat : plan.seats()) {
            SeatCategory category = categoriesByCode.get(seat.categoryCode());
            if (category == null) {
                throw new ValidationException("Unknown seat category: " + seat.categoryCode());
            }
            seats.add(new LayoutSeat(this, seat, category));
        }
        gridRows = plan.gridRows();
        gridCols = plan.gridCols();
        totalSeats = Math.toIntExact(plan.sellableSeats());
    }

    public void activate() {
        status = status.transitionTo(LayoutStatus.ACTIVE);
    }

    public void retire() {
        status = status.transitionTo(LayoutStatus.RETIRED);
    }

    private void requireDraft() {
        if (status != LayoutStatus.DRAFT) {
            throw new InvalidStateException("Only draft layouts can be edited; layout " + id + " is " + status);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getScreenId() {
        return screenId;
    }

    public int getVersion() {
        return version;
    }

    public LayoutStatus getStatus() {
        return status;
    }

    public int getGridRows() {
        return gridRows;
    }

    public int getGridCols() {
        return gridCols;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public List<LayoutSeat> getSeats() {
        return Collections.unmodifiableList(seats);
    }
}
