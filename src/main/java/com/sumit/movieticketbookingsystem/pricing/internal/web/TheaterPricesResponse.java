package com.sumit.movieticketbookingsystem.pricing.internal.web;

import java.util.Map;

/** Price in paise by category code. */
record TheaterPricesResponse(Map<String, Long> prices) {
}
