package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import com.sumit.movieticketbookingsystem.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDate;

@Entity
public class Movie extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(name = "duration_min")
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    private Certification certification;

    private LocalDate releaseDate;

    private boolean active = true;

    protected Movie() {
    }

    public Movie(String title, int durationMinutes, Certification certification, LocalDate releaseDate) {
        update(title, durationMinutes, certification, releaseDate);
    }

    public void update(String title, int durationMinutes, Certification certification, LocalDate releaseDate) {
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.certification = certification;
        this.releaseDate = releaseDate;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Certification getCertification() {
        return certification;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public boolean isActive() {
        return active;
    }
}
