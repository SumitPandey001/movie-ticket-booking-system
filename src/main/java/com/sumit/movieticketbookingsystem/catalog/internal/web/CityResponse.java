package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.City;

record CityResponse(long id, String name, String state, String timezone, boolean active) {

    static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getState(),
                city.getTimezone().getId(), city.isActive());
    }
}
