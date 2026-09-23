package com.sumit.movieticketbookingsystem.inventory;

import com.sumit.movieticketbookingsystem.shared.error.DomainException;
import com.sumit.movieticketbookingsystem.shared.error.ErrorCode;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Some of the requested seats are taken or momentarily locked by another customer. Nothing was claimed.
 */
public class SeatsUnavailableException extends DomainException {

    private final Set<Long> seatIds;

    public SeatsUnavailableException(Set<Long> seatIds) {
        super(ErrorCode.SEATS_UNAVAILABLE, seatIds.size() == 1
                ? "1 of the selected seats was just taken"
                : seatIds.size() + " of the selected seats were just taken");
        this.seatIds = new TreeSet<>(seatIds);
    }

    public Set<Long> seatIds() {
        return seatIds;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("unavailableSeatIds", seatIds);
    }
}
