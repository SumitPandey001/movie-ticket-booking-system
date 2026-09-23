package com.sumit.movieticketbookingsystem.catalog;

import java.time.Duration;

public record MovieInfo(long movieId, String title, Duration duration, String certification, boolean active) {
}
