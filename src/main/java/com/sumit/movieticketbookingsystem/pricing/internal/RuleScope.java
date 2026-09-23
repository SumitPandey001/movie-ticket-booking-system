package com.sumit.movieticketbookingsystem.pricing.internal;

/**
 * Where a day rule applies. When several apply, the most specific wins: THEATER, then CITY, then GLOBAL.
 */
enum RuleScope {
    GLOBAL,
    CITY,
    THEATER
}
