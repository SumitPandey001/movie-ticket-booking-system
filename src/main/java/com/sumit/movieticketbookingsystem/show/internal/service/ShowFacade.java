package com.sumit.movieticketbookingsystem.show.internal.service;

import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.show.ShowApi;
import com.sumit.movieticketbookingsystem.show.ShowDetails;
import com.sumit.movieticketbookingsystem.show.internal.domain.Show;
import com.sumit.movieticketbookingsystem.show.internal.domain.ShowStatus;
import com.sumit.movieticketbookingsystem.show.internal.persistence.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ShowFacade implements ShowApi {

    private final ShowRepository shows;

    ShowFacade(ShowRepository shows) {
        this.shows = shows;
    }

    @Override
    public ShowDetails show(long showId) {
        Show show = shows.findById(showId).orElseThrow(() -> new NotFoundException("Show", showId));
        return new ShowDetails(show.getId(), show.getMovieId(), show.getTheaterId(), show.getCityId(),
                show.getLayoutId(), show.getShowDate(), show.getStartTime(), show.getStatus() == ShowStatus.OPEN,
                show.getRefundPolicyId());
    }
}
