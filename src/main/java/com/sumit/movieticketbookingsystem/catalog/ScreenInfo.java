package com.sumit.movieticketbookingsystem.catalog;

/**
 * @param active         the screen, its theater and its city are all active
 * @param activeLayoutId null when the screen has no active layout yet
 */
public record ScreenInfo(long screenId, String name, long theaterId, long cityId, boolean active,
                         Long activeLayoutId) {
}
