package com.sumit.movieticketbookingsystem.show.internal.web;

import com.sumit.movieticketbookingsystem.show.internal.domain.Show;
import com.sumit.movieticketbookingsystem.show.internal.domain.ShowStatus;

import java.time.Instant;
import java.time.LocalDate;

record ShowResponse(long id, long movieId, long screenId, long theaterId, long cityId, long layoutId,
                    LocalDate showDate, Instant startTime, Instant endTime, String language, String format,
                    ShowStatus status, int totalSeats, Long priceFromPaise) {

    static ShowResponse from(Show show) {
        return new ShowResponse(show.getId(), show.getMovieId(), show.getScreenId(), show.getTheaterId(),
                show.getCityId(), show.getLayoutId(), show.getShowDate(), show.getStartTime(), show.getEndTime(),
                show.getLanguage(), show.getFormat(), show.getStatus(), show.getTotalSeats(),
                show.getPriceFromPaise());
    }
}
