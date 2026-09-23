package com.sumit.movieticketbookingsystem.catalog;

import java.util.List;

/**
 * What other modules may ask the catalog. Every lookup throws {@code NotFoundException} for an unknown id.
 */
public interface CatalogApi {

    ScreenInfo screen(long screenId);

    LayoutView layout(long layoutId);

    CitySummary city(long cityId);

    MovieInfo movie(long movieId);

    /** All seat categories, cheapest tier first (REGULAR, PREMIUM, RECLINER). */
    List<SeatCategoryInfo> seatCategories();
}
