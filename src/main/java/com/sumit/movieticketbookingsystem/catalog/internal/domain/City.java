package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.persistence.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.ZoneId;

@Entity
public class City extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String state;

    // stored as the zone id string, e.g. "Asia/Kolkata"
    private String timezone;

    private boolean active = true;

    protected City() {
    }

    public City(String name, String state, ZoneId timezone) {
        update(name, state, timezone);
    }

    public void update(String name, String state, ZoneId timezone) {
        this.name = name;
        this.state = state;
        this.timezone = timezone.getId();
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public ZoneId getTimezone() {
        return ZoneId.of(timezone);
    }

    public boolean isActive() {
        return active;
    }
}
