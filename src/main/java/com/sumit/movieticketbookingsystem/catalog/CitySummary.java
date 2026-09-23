package com.sumit.movieticketbookingsystem.catalog;

import java.time.ZoneId;

public record CitySummary(long cityId, String name, ZoneId zone, boolean active) {
}
