package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.pricing.ShowPricing;
import com.sumit.movieticketbookingsystem.show.internal.domain.Show;

final class ShowPricings {

    private ShowPricings() {
    }

    static ShowPricing of(Show show) {
        return new ShowPricing(show.getId(), show.getMovieId(), show.getCityId(), show.getTheaterId(),
                show.getShowDate());
    }
}
