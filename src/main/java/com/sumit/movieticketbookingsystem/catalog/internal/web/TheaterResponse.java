package com.sumit.movieticketbookingsystem.catalog.internal.web;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.Screen;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Theater;

import java.util.List;

record TheaterResponse(long id, long cityId, String name, String area, String address, boolean active,
                       List<ScreenResponse> screens) {

    static TheaterResponse from(Theater theater) {
        return new TheaterResponse(theater.getId(), theater.getCityId(), theater.getName(), theater.getArea(),
                theater.getAddress(), theater.isActive(),
                theater.getScreens().stream().map(ScreenResponse::from).toList());
    }

    record ScreenResponse(long id, String name, boolean active) {

        static ScreenResponse from(Screen screen) {
            return new ScreenResponse(screen.getId(), screen.getName(), screen.isActive());
        }
    }
}
