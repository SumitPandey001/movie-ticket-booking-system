package com.sumit.movieticketbookingsystem.catalog.internal.service;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.City;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Screen;
import com.sumit.movieticketbookingsystem.catalog.internal.domain.Theater;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.CityRepository;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.TheaterRepository;
import com.sumit.movieticketbookingsystem.shared.error.AlreadyExistsException;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TheaterAdminService {

    private final TheaterRepository theaters;
    private final CityRepository cities;

    public TheaterAdminService(TheaterRepository theaters, CityRepository cities) {
        this.theaters = theaters;
        this.cities = cities;
    }

    @Transactional(readOnly = true)
    public Theater theater(long id) {
        return find(id);
    }

    /** Every theater in the city, inactive ones included, with its screens. */
    @Transactional(readOnly = true)
    public List<Theater> theaters(long cityId) {
        return theaters.findByCityIdOrderByName(cityId);
    }

    @Transactional
    public Theater create(long cityId, String name, String area, String address) {
        City city = cities.findById(cityId).orElseThrow(() -> new NotFoundException("City", cityId));
        if (!city.isActive()) {
            throw new ValidationException("City " + cityId + " is inactive");
        }
        String theaterName = name.strip();
        if (theaters.existsByCityIdAndNameIgnoreCase(cityId, theaterName)) {
            throw new AlreadyExistsException("Theater", theaterName);
        }
        return theaters.save(new Theater(cityId, theaterName, area, address));
    }

    @Transactional
    public Theater update(long id, String name, String area, String address) {
        Theater theater = find(id);
        String theaterName = name.strip();
        if (theaters.existsByCityIdAndNameIgnoreCaseAndIdNot(theater.getCityId(), theaterName, id)) {
            throw new AlreadyExistsException("Theater", theaterName);
        }
        theater.update(theaterName, area, address);
        return theater;
    }

    @Transactional
    public void deactivate(long id) {
        find(id).deactivate();
    }

    @Transactional
    public Screen addScreen(long theaterId, String name) {
        Screen screen = find(theaterId).addScreen(name.strip());
        theaters.flush();   // so the new screen has its generated id for the response
        return screen;
    }

    @Transactional
    public Screen renameScreen(long screenId, String name) {
        return findByScreen(screenId).renameScreen(screenId, name.strip());
    }

    @Transactional
    public void deactivateScreen(long screenId) {
        findByScreen(screenId).deactivateScreen(screenId);
    }

    private Theater find(long id) {
        return theaters.findById(id).orElseThrow(() -> new NotFoundException("Theater", id));
    }

    private Theater findByScreen(long screenId) {
        return theaters.findByScreenId(screenId).orElseThrow(() -> new NotFoundException("Screen", screenId));
    }
}
