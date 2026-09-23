package com.sumit.movieticketbookingsystem.catalog.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Reference data seeded by migration (REGULAR, PREMIUM, RECLINER); not editable through the API.
 */
@Entity
public class SeatCategory {

    @Id
    private Long id;

    private String code;

    private String name;

    private int sortOrder;

    protected SeatCategory() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
