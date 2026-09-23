package com.sumit.movieticketbookingsystem.catalog.internal.service;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.LayoutPlan;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.LayoutStatus;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Screen;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatCategory;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.SeatLayout;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.SeatCategoryRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.SeatLayoutRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.TheaterRepository;
import com.sumit.movieticketbookingsystem.shared.error.InvalidStateException;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LayoutAdminService {

    private final SeatLayoutRepository layouts;
    private final SeatCategoryRepository categories;
    private final TheaterRepository theaters;

    public LayoutAdminService(SeatLayoutRepository layouts, SeatCategoryRepository categories,
            TheaterRepository theaters) {
        this.layouts = layouts;
        this.categories = categories;
        this.theaters = theaters;
    }

    @Transactional(readOnly = true)
    public List<SeatLayout> layouts(long screenId) {
        screen(screenId);
        return layouts.findByScreenIdOrderByVersion(screenId);
    }

    @Transactional
    public SeatLayout createDraft(long screenId, LayoutPlan plan) {
        if (!screen(screenId).isActive()) {
            throw new ValidationException("Screen " + screenId + " is inactive");
        }
        // ponytail: two admins drafting the same screen at the same moment get a 500 from UNIQUE(screen_id, version);
        // lock the screen row here if that ever matters
        int version = layouts.latestVersion(screenId) + 1;
        return layouts.save(SeatLayout.draft(screenId, version, plan, categoriesByCode()));
    }

    @Transactional
    public SeatLayout editDraft(long layoutId, LayoutPlan plan) {
        SeatLayout layout = find(layoutId);
        layout.clearSeats();
        layouts.flush();    // delete the old seats before inserting new ones with the same labels
        layout.addSeats(plan, categoriesByCode());
        return layout;
    }

    @Transactional
    public SeatLayout activate(long layoutId) {
        SeatLayout layout = find(layoutId);
        if (layout.getStatus() != LayoutStatus.DRAFT) {
            throw new InvalidStateException("Only draft layouts can be activated; layout " + layoutId
                    + " is " + layout.getStatus());
        }
        // seat_layout_one_active is checked per statement, so the old layout must be retired in the database first
        layouts.findByScreenIdAndStatus(layout.getScreenId(), LayoutStatus.ACTIVE).ifPresent(current -> {
            current.retire();
            layouts.flush();
        });
        layout.activate();
        return layout;
    }

    private SeatLayout find(long layoutId) {
        return layouts.findById(layoutId).orElseThrow(() -> new NotFoundException("Seat layout", layoutId));
    }

    private Screen screen(long screenId) {
        return theaters.findByScreenId(screenId)
                .orElseThrow(() -> new NotFoundException("Screen", screenId))
                .screen(screenId);
    }

    private Map<String, SeatCategory> categoriesByCode() {
        return categories.findAll().stream()
                .collect(Collectors.toMap(SeatCategory::getCode, Function.identity()));
    }
}
