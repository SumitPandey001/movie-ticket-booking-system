package com.sumit.movieticketbookingsystem.catalog;

/**
 * What other modules may ask the catalog. Every lookup throws {@code NotFoundException} for an unknown id.
 */
public interface CatalogApi {

    ScreenInfo screen(long screenId);

    LayoutView layout(long layoutId);

    CitySummary city(long cityId);

    MovieInfo movie(long movieId);
}
