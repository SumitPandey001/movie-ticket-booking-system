package com.sumit.movieticketbookingsystem.catalog.internal.service;

import com.sumit.movieticketbookingsystem.catalog.internal.domain.City;
import com.sumit.movieticketbookingsystem.catalog.internal.persistence.CityRepository;
import com.sumit.movieticketbookingsystem.shared.error.AlreadyExistsException;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

@Service
public class CityService {

    private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Asia/Kolkata");

    private final CityRepository cities;

    public CityService(CityRepository cities) {
        this.cities = cities;
    }

    @Transactional(readOnly = true)
    public List<City> activeCities() {
        return cities.findByActiveTrueOrderByName();
    }

    @Transactional
    public City create(String name, String state, String timezone) {
        String cityName = name.strip();
        if (cities.existsByNameIgnoreCase(cityName)) {
            throw new AlreadyExistsException("City", cityName);
        }
        return cities.save(new City(cityName, state, zone(timezone)));
    }

    @Transactional
    public City update(long id, String name, String state, String timezone) {
        City city = find(id);
        String cityName = name.strip();
        if (cities.existsByNameIgnoreCaseAndIdNot(cityName, id)) {
            throw new AlreadyExistsException("City", cityName);
        }
        city.update(cityName, state, zone(timezone));
        return city;
    }

    @Transactional
    public void deactivate(long id) {
        find(id).deactivate();
    }

    private City find(long id) {
        return cities.findById(id).orElseThrow(() -> new NotFoundException("City", id));
    }

    private static ZoneId zone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(timezone.strip());
        } catch (DateTimeException e) {
            throw new ValidationException("Unknown timezone: " + timezone);
        }
    }
}
