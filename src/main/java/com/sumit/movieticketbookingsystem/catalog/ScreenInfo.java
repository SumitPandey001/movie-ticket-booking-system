package com.sumit.movieticketbookingsystem.catalog;

public record ScreenInfo(long screenId, String name, long theaterId, long cityId, boolean active,
                         Long activeLayoutId) {
}
