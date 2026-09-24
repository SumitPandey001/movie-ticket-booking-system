package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.error.AlreadyExistsException;
import com.sumit.movieticketbookingsystem.shared.error.NotFoundException;
import com.sumit.movieticketbookingsystem.shared.error.ValidationException;
import com.sumit.movieticketbookingsystem.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
public class Theater extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long cityId;

    private String name;

    private String area;

    private String address;

    private boolean active = true;

    @OneToMany(mappedBy = "theater", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<Screen> screens = new ArrayList<>();

    protected Theater() {
    }

    public Theater(Long cityId, String name, String area, String address) {
        this.cityId = cityId;
        update(name, area, address);
    }

    public void update(String name, String area, String address) {
        this.name = name;
        this.area = area;
        this.address = address;
    }

    public void deactivate() {
        this.active = false;
    }

    public Screen addScreen(String screenName) {
        if (!active) {
            throw new ValidationException("Theater " + id + " is inactive");
        }
        requireUniqueScreenName(screenName, null);
        Screen screen = new Screen(this, screenName);
        screens.add(screen);
        return screen;
    }

    public Screen renameScreen(long screenId, String screenName) {
        Screen screen = screen(screenId);
        requireUniqueScreenName(screenName, screen);
        screen.rename(screenName);
        return screen;
    }

    public void deactivateScreen(long screenId) {
        screen(screenId).deactivate();
    }

    public Screen screen(long screenId) {
        return screens.stream()
                .filter(screen -> Objects.equals(screen.getId(), screenId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Screen", screenId));
    }

    private void requireUniqueScreenName(String screenName, Screen except) {
        boolean taken = screens.stream()
                .anyMatch(screen -> screen != except && screen.getName().equalsIgnoreCase(screenName));
        if (taken) {
            throw new AlreadyExistsException("Screen", screenName);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCityId() {
        return cityId;
    }

    public String getName() {
        return name;
    }

    public String getArea() {
        return area;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }

    public List<Screen> getScreens() {
        return Collections.unmodifiableList(screens);
    }
}
