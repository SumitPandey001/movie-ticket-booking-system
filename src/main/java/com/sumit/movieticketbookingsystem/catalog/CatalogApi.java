package com.sumit.movieticketbookingsystem.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CatalogApi {

    ScreenInfo screen(long screenId);

    LayoutView layout(long layoutId);

    CitySummary city(long cityId);

    MovieInfo movie(long movieId);

    Map<Long, MovieInfo> movies(Collection<Long> movieIds);

    Map<Long, TheaterSummary> theaters(Collection<Long> theaterIds);

    List<SeatCategoryInfo> seatCategories();
}
