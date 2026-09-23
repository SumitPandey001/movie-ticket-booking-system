package com.sumit.movieticketbookingsystem.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * What other modules may ask the catalog. Every lookup throws {@code NotFoundException} for an unknown id.
 */
public interface CatalogApi {

    ScreenInfo screen(long screenId);

    LayoutView layout(long layoutId);

    CitySummary city(long cityId);

    MovieInfo movie(long movieId);

    /** Batch lookups for listing pages; unknown ids are simply missing from the map. */
    Map<Long, MovieInfo> movies(Collection<Long> movieIds);

    Map<Long, TheaterSummary> theaters(Collection<Long> theaterIds);

    /** All seat categories, cheapest tier first (REGULAR, PREMIUM, RECLINER). */
    List<SeatCategoryInfo> seatCategories();
}
