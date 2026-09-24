package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.catalog.CatalogApi;
import com.sumit.movieticketbookingsystem.catalog.LayoutView;
import com.sumit.movieticketbookingsystem.inventory.InventoryApi;
import com.sumit.movieticketbookingsystem.inventory.SeatStatus;
import com.sumit.movieticketbookingsystem.pricing.PricingApi;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.show.internal.domain.Show;
import com.sumit.movieticketbookingsystem.show.internal.domain.ShowStatus;
import com.sumit.movieticketbookingsystem.show.internal.persistence.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The seat picker for one show: layout grid (catalog) + live seat statuses (inventory) + prices (pricing).
 * Never cached; statuses always come straight from the database, with lapsed holds shown as available.
 */
@Service
@Transactional(readOnly = true)
public class SeatMapService {

    private final ShowRepository shows;
    private final CatalogApi catalog;
    private final InventoryApi inventory;
    private final PricingApi pricing;
    private final Clock clock;

    SeatMapService(ShowRepository shows, CatalogApi catalog, InventoryApi inventory, PricingApi pricing,
            Clock clock) {
        this.shows = shows;
        this.catalog = catalog;
        this.inventory = inventory;
        this.pricing = pricing;
        this.clock = clock;
    }

    /** Only open shows have a public seat map; anything else is reported as not found. */
    public SeatMap seatMap(long showId) {
        Show show = shows.findById(showId)
                .filter(candidate -> candidate.getStatus() == ShowStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("Show", showId));

        LayoutView layout = catalog.layout(show.getLayoutId());
        Map<Long, SeatStatus> statuses = inventory.seatStatuses(showId, Instant.now(clock));
        Map<Long, Long> prices = pricing.displayPrices(ShowPricings.of(show));

        List<Category> categories = catalog.seatCategories().stream()
                .filter(category -> prices.containsKey(category.categoryId()))
                .map(category -> new Category(category.categoryId(), category.code(), category.name(),
                        prices.get(category.categoryId())))
                .toList();
        List<Seat> seats = layout.seats().stream()
                .map(seat -> new Seat(seat.layoutSeatId(), seat.label(), seat.gridRow(), seat.gridCol(),
                        seat.categoryId(), seat.type(), statuses.get(seat.layoutSeatId())))
                .toList();
        return new SeatMap(showId, catalog.screen(show.getScreenId()).name(), layout.gridRows(), layout.gridCols(),
                categories, seats);
    }

    public record SeatMap(long showId, String screen, int gridRows, int gridCols, List<Category> categories,
                          List<Seat> seats) {
    }

    /** pricePaise includes the day's surcharge; fees and GST are added when seats are held. */
    public record Category(long id, String code, String name, long pricePaise) {
    }

    /** type is NORMAL, WHEELCHAIR or BLOCKED; whether it can be picked is status. */
    public record Seat(long seatId, String label, int row, int col, long categoryId, String type, SeatStatus status) {
    }
}
